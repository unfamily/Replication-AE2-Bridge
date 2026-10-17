---
navigation:
    parent: index.md
    title: Matter Assembler
    icon: rep_ae2_bridge:matter_assembler
    position: 20
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:matter_assembler
---

# Matter Assembler

<Row gap="20">
<BlockImage id="rep_ae2_bridge:matter_assembler" scale="8"></BlockImage>
</Row>

The Matter Assembler is a fast craft worker on your **ME network**. Together with a Bridge, it can take Replication jobs and finish them much quicker than a normal Replicator.

## How it works

- Connect it to the same ME network as your Bridge
- It picks up work from Replication (for example from a Replication terminal) and from AE2 crafting requests that go through the Bridge
- Jobs started from Replication deliver finished items back to Replication
- Jobs started from AE2 deliver finished items into the ME system

## Upgrades and priority

Open the Matter Assembler to insert **acceleration cards** for more speed, and use the **priority** tab if you have several Assemblers and want some to take work first.

## Tips

- It needs a Bridge on the same ME grid (chip patterns and Replication tasks come from the Bridge)
- If the destination has no space, the Assembler waits until it can deliver
