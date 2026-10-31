---
navigation:
    parent: index.md
    title: Matter Assembling Chamber
    icon: rep_ae2_bridge:chamber_frame
    position: 30
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:chamber_frame
- rep_ae2_bridge:replication_unit
---

# Matter Assembling Chamber

Build a hollow cuboid (config size 3–7) with **Chamber Frames** on edges/corners, **AE2 quartz glass** (or vibrant glass) on faces, and **Replication Units** inside.

## Structure

- Edges and corners: Chamber Frame (column texture when stacked vertically)
- Faces: `ae2:quartz_glass` or `ae2:quartz_vibrant_glass` (or frames)
- Interior: Replication Units and/or air (hollow allowed)

When valid, units keep a normal cube plus a fixed overlay and an idle / running / anomaly color overlay, with a matter-shader fill **only inside the units**. The whole chamber uses a single AE2 channel (cable on the frame).

## Example — 5×5×5

Rotate the scene to inspect each face. Outer size **5×5×5**: frames on every edge/corner, quartz glass on the face panels, replication units filling the **3×3×3** interior.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/matter_assembling_chamber_5x5.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| Layer | Blocks |
|-------|--------|
| Edges / corners | Chamber Frame |
| Face panels | AE2 Quartz Glass |
| Interior (3×3×3) | Replication Unit |
