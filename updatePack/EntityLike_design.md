# EntityLike compatibility — concrete design for HBs Foundation + HBs Aero Waypoints

Grounded in the current code. The one-line summary: **the waypoint "seam" is
following a UUID, not holding an `Entity`.** Foundation resolves a followed
UUID to a position through an ordered list of resolvers; Aero Waypoints adds
the Sable/Aeronautics resolver at runtime. Nothing in Foundation ever imports
Sable.

---

## Where things live today (the facts this design leans on)

- **Foundation `com.holybuckets.foundation.core.MovingWaypoint`** — server-side
  waypoint API. `MovingWaypoint.setWaypoint(sp, pos, colorId, waypointId,
  follow, Entity entity, tag)` takes a raw `Entity`. This is the only place a
  moving target's identity enters the system.
- **Foundation `client.core.MovingWaypoint.Waypoint`** — client render model.
  Already carries `linkedEntityUuid`, `targetPos`, `isActive`. The client
  follows the target by looking that UUID up in the client level.
- **Aero `core.ITrackedContrap`** — wraps a Create `Contraption`. Exposes
  `getContraptionEntity()` (the `AbstractContraptionEntity`),
  `getContraptionUuid()`, `getAnchorPos()`. `WaypointManager.track(...)` passes
  `contrap.getContraptionEntity()` straight into `MovingWaypoint.setWaypoint`.
- **Aero `WaypointManager`** — already has the "target went away → park at last
  block pos → expire after `STATIC_WAYPOINT_LIFETIME_TICKS`" lifecycle
  (`tickPrune` → `transitionToStatic` → `expireStatic`). Reuse this; don't
  invent a second grace mechanism.

So the change is small: make Foundation follow a **UUID resolved to an
`EntityLike`** instead of a concrete `Entity`, and keep the existing `Entity`
overload as a thin wrapper so the Create path is untouched.

---

## Part 1 — HBs Foundation

### 1. `EntityLike` (interface) — `com.holybuckets.foundation.core`
Read-only view of a followable target. Only what `MovingWaypoint` needs:

- `UUID getUUID()`
- `Vec3 position()`
- orientation — match whatever `MovingWaypoint` uses today (`float getYRot()` /
  `getXRot()`); if it doesn't use orientation, omit it
- `ResourceLocation dimension()` (the dimension key it is *currently* in — do
  not assume the caller's `Level`)
- `boolean isValid()` — false once removed/disassembled/unloaded-for-good

### 2. `VanillaEntityLike` (built-in impl) — same package
Wraps a vanilla `Entity`; delegates each method (`entity.getUUID()`,
`entity.position()`, `entity.level().dimension().location()`,
`!entity.isRemoved()`). This is a straight lift of today's behavior.

### 3. `EntityLikeResolver` (interface) — same package
Single method:

```
Optional<EntityLike> resolve(UUID uuid, MinecraftServer server)
```

`MinecraftServer` (not `Level`) because targets may change dimension — the
resolver decides which level/registry to consult.

### 4. Resolver list — a small holder class, e.g. `EntityLikeResolvers`
- Private `static final List<EntityLikeResolver>`.
- `register(EntityLikeResolver)` → append.
- `Optional<EntityLike> resolve(UUID, MinecraftServer)` → loop in order, return
  first non-empty.
- On class init, register the **vanilla resolver first** (it does
  `server.getLevel(dim).getEntity(uuid)` scanning loaded levels, or reuses
  whatever lookup `MovingWaypoint` does now). No priorities, no keys — append
  order, cheapest/most-common first.

### 5. Rewire `MovingWaypoint` to follow a UUID
- Add `setWaypoint(sp, pos, colorId, waypointId, follow, UUID targetUuid, tag)`.
- Keep the existing `Entity` overload; implement it as
  `setWaypoint(..., entity.getUUID(), ...)` — Create path compiles unchanged.
- Wherever `MovingWaypoint` currently does `level.getEntity(uuid)` to refresh a
  moving waypoint's position (server tick and/or the client follow path),
  replace with `EntityLikeResolvers.resolve(uuid, server)` and read
  `position()` / `dimension()` off the `EntityLike`. Everything downstream
  (render, distance, GUI, persistence) consumes `EntityLike` only.
- Client side: `linkedEntityUuid` already exists; the client's per-frame
  "where is it now" lookup is the other spot that must go through the resolver
  path (client uses its own client-level vanilla resolver; Sable's client
  resolver is registered the same way if ships render client-side).

### 6. Stale handling — reuse, don't reinvent
A single failed `resolve` is not death (a `SubLevel` can be briefly unloaded).
Give `MovingWaypoint` a consecutive-miss counter with a grace window, then hand
off to the **existing** Aero lifecycle: a target that stays unresolved parks at
its last-known `pos` (Aero's `transitionToStatic`) and expires on the existing
timer (`expireStatic`). Foundation only needs "N consecutive misses ⇒ report
lost"; Aero already owns what to do about it.

---

## Part 2 — HBs Aero Waypoints

### 1. `SableSubLevelEntityLike implements EntityLike`
New package `com.holybuckets.aerowaypoint.compat.aeronautics`. Wraps a Sable
`SubLevel`; delegates:
- `getUUID()` → the SubLevel's id
- `position()` → SubLevel world-space origin/center (whatever Sable exposes)
- orientation → SubLevel rotation, if present; else identity
- `dimension()` → the parent dimension the ship is in
- `isValid()` → SubLevel still loaded/simulated

*Only place in the whole system that imports Sable.*

### 2. `SableSubLevelResolver implements EntityLikeResolver`
`resolve(uuid, server)` → ask Sable for the `SubLevel` with that id (global
lookup if ships cross dimensions, per-level otherwise), wrap in the adapter,
else `Optional.empty()`.

### 3. Register at init — one call
In `CommonClass.init()` (or `AeroWaypointsMain.init()`), alongside the existing
Balm registrations:
`EntityLikeResolvers.register(new SableSubLevelResolver());`
Internal call between two of your own mods — no event bus, no public extension
point.

### 4. Waypoint creation for Aeronautics ships
Today the Create path: `AbstractContraptionEntityMixin` fires an interact →
`WaypointManager.interact` → `ITrackedContrap.getContraption(entity)` →
`track(...)` → `setWaypoint(..., contraptionEntity, ...)`.

For ships, add the parallel path where the interaction target resolves to a
Sable ship: source the **SubLevel's UUID** and call the new
`setWaypoint(..., UUID, ...)` overload. Simplest fit with existing code: a
Sable-backed `ITrackedContrap` impl whose `getContraptionUuid()` returns the
SubLevel id and `getContraptionEntity()` returns `null`, so `track(...)` takes
the "no entity → follow by UUID" branch. Foundation is never told it's a ship —
the resolver list figures that out.

---

## Open questions to resolve against the real APIs (pre-code)

1. **Sable `SubLevel` surface** — exact getters for id, world position,
   rotation, validity, and how to look one up by UUID (global vs per-level).
   Drives the adapter + resolver.
2. **Aeronautics interaction detection** — how Aero knows a right-click hit a
   ship and gets its SubLevel id. Is an Aeronautics ship still an
   `AbstractContraptionEntity` (so the existing mixin fires), or a different
   entity/hit path that needs its own hook?
3. **Confirm `MovingWaypoint` internals in Foundation** — verify it actually
   re-resolves the entity by UUID each tick (this design assumes that is the
   lookup being replaced); confirm whether orientation is used at all.

## Build order
Foundation first (interface + vanilla resolver + `setWaypoint(UUID)` overload +
resolver loop, published to mavenLocal), then Aero (adapter + resolver +
registration + ship interaction path), then the three manual tests in the
guide's checklist (Create regression, ship incl. dimension change, ship
destroyed → graceful loss).
