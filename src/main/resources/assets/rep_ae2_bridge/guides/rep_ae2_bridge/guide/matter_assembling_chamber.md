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

## Crafting pieces

- Frame: iron + Replica Ingot + Fluixing Replica Alloy (shaped, with A/B swap variant)
- Replication Unit: shapeless Crafting Unit + Replica Processor + Matter Assembler
