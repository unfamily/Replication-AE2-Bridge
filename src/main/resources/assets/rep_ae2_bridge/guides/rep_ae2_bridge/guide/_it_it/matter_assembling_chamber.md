---
navigation:
    parent: index.md
    title: Camera di Assemblaggio Materia
    icon: rep_ae2_bridge:chamber_frame
    position: 30
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:chamber_frame
- rep_ae2_bridge:replication_unit
---

# Camera di Assemblaggio Materia

Costruisci un cuboide cavo (lato 3–7 da config) con **Telaio Camera** su spigoli/angoli, **vetro di quarzo AE2** (o vibrant) sulle facce e **Unità di Replicazione** all’interno.

## Struttura

- Spigoli e angoli: Telaio Camera (texture colonna se impilato in verticale)
- Facce: `ae2:quartz_glass` o `ae2:quartz_vibrant_glass` (oppure telai)
- Interno: Unità di Replicazione e/o aria (cavo ammesso)

Quando è valida, le unità restano un cubo normale con overlay fisso + overlay idle / running / anomaly, e fill shader materia **solo dentro le unità**. Tutta la chamber usa un solo canale AE2 (cavo sul frame).

## Esempio — 5×5×5

Ruota la scena per ispezionare ogni faccia. Dimensione esterna **5×5×5**: telai su ogni spigolo/angolo, vetro di quarzo sui pannelli delle facce, unità di replicazione che riempiono l’interno **3×3×3**.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/matter_assembling_chamber_5x5.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| Livello | Blocchi |
|---------|---------|
| Spigoli / angoli | Telaio Camera |
| Pannelli faccia | Vetro di quarzo AE2 |
| Interno (3×3×3) | Unità di Replicazione |
