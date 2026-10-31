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

## Craft pezzi

- Telaio: ferro + Lingotto Replica + Fluixing Replica Alloy (shaped, con variante A/B scambiati)
- Unità: shapeless Crafting Unit + Processore Replica + Assemblatore di Materia
