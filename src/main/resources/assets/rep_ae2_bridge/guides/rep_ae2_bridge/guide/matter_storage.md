---
navigation:
    parent: index.md
    title: Matter Storage
    icon: rep_ae2_bridge:matter_storage_cell_1k
    position: 30
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:matter_cell_housing
- rep_ae2_bridge:matter_storage_cell_1k
- rep_ae2_bridge:matter_storage_cell_4k
- rep_ae2_bridge:matter_storage_cell_16k
- rep_ae2_bridge:matter_storage_cell_64k
- rep_ae2_bridge:matter_storage_cell_256k
- rep_ae2_bridge:matter_storage_cell_creative
---

# Matter Storage

<Row gap="20">
<ItemImage id="rep_ae2_bridge:matter_storage_cell_1k" scale="4"></ItemImage>
<ItemImage id="rep_ae2_bridge:matter_storage_cell_4k" scale="4"></ItemImage>
<ItemImage id="rep_ae2_bridge:matter_storage_cell_16k" scale="4"></ItemImage>
</Row>

Matter storage cells keep Replication matter inside your **ME system**, similar to how normal cells store items. With a Bridge connected, matter in those cells is available to the matter network as well.

## Cells and housing

- Build cells from a **Matter Cell Housing** and the matching AE2 cell component (1k / 4k / 16k / 64k / 256k)
- Capacity matches that component — larger components hold more matter
- You can also craft a cell in one step with the housing pattern and a component in the center
- The **Creative Matter Storage Cell** provides unlimited amounts of every matter type (creative / testing)

Place the cells in ME drives or chests like any other storage cell. Terminals can filter the Matter type separately from items and fluids.

## Import, export, and storage bus

AE2 **Import Bus**, **Export Bus**, and **Storage Bus** can move matter against adjacent Replication **matter tanks** and **Disintegrators** when this addon is loaded. Enable the Matter key type on the bus like you would for fluids.

A **Storage Bus** on a matter tank both **lists** the tank contents in the ME system and can insert or extract matter (bidirectional). Import Bus pulls tank → ME; Export Bus pushes ME → tank.

## Disintegration patterns (item → matter)

On the **Pattern Encoding Terminal**, use the extra **Matter** tab (blueprint icon):

- Put an item in the input (raise the count to scale outputs)
- The panel previews matter amounts (not selectable)
- Encode produces a normal AE2 **processing pattern**

Place that pattern in a **Pattern Provider** facing a **Disintegrator**. Pull the resulting matter with an **Import Bus** (or Storage Bus) on the Disintegrator. The Bridge also mirrors matter from the Replication network into ME as stock.

## JEI matter items

Virtual matter items from this addon are JEI helpers. Dragging them into AE2 filters or monitors configures the real **Matter** key automatically.

## Storage monitors

Right-click an unlocked **Storage Monitor** while holding a Replication **Matter Tank** or a virtual matter item:

- Filled tank / matter item → that matter type
- Empty tank on an empty monitor → AE2 configures the tank item itself
- Empty tank when something is already set → first matter type, then cycle

## Autocrafting and matter visibility

With a Bridge, AE2 craft jobs reserve matter without pulling it out of ME. Stock stays in cells / tanks / buses so Replication can still see it; the real drain happens when the pattern runs. Cancel clears the phantom reservation and does not duplicate matter.

## Tips

- Matter shown through the Bridge and matter stored in cells is the same pool for Replication machines that use the Bridge
- Autocraft jobs that output matter need an Import/Storage Bus on the Disintegrator; Bridge mirror alone shows stock but does not finish Pattern Provider output collection
- Keep enough cell space if you move large amounts of matter through AE2
