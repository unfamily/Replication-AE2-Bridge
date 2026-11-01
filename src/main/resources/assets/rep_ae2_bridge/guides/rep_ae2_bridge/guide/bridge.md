---
navigation:
    parent: index.md
    title: AE2–Replication Bridge
    icon: rep_ae2_bridge:rep_ae2_bridge
    position: 10
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:rep_ae2_bridge
- rep_ae2_bridge:cable_rep_ae2_bridge
---

# AE2–Replication Bridge

<Row gap="20">
<BlockImage id="rep_ae2_bridge:rep_ae2_bridge" scale="8"></BlockImage>
</Row>

The Bridge is the link between your **ME network** and a **Replication matter network**. Place it where both can reach it: an ME cable on one side, a matter pipe on another.

It is also available as a cable part (`cable_rep_ae2_bridge`) that mounts on an ME cable bus; craft it shapelessly from the full block (and back). Matter pipes connect to the part's external face.

## What it does

- Shows Replication matter in AE2 terminals (as its own matter type)
- Lets AE2 autocrafting request items through Replication
- Can power the matter network with spare AE energy when you enable it (respects AE2’s FE conversion ratio; stops if the ME grid is energy-starved)
- Accepts finished items from Replicators and Matter Assemblers and stores them in the ME system when space is available

## Using the interface

Right-click the Bridge to open its screen.

- **Power Matter Network** — toggle that sends spare AE power into the matter network as FE (dynamic; uses AE2’s FE ratio)
- **Priority** — same priority tab as other AE2 machines; higher values are preferred when the network chooses where work goes

If the ME system is temporarily full, finished items can sit in the Bridge’s buffer and move into storage when there is room again.

## Autocrafting matter

When AE2 starts a Replication craft, matter stays in your cells / tanks / Storage Bus (the CPU only holds a phantom reservation). Replication still sees that stock until the pattern actually runs and drains it. Cancelling a craft does not duplicate matter.

## Tips

- One Bridge per matter network is usually enough
- Keep the Bridge online on both the ME network and the matter pipes
- Pair it with Matter Assemblers on the same ME network for faster Replication jobs
