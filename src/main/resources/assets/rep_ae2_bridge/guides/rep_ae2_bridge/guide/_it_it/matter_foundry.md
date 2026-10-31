---
navigation:
    parent: index.md
    title: Fonderia di Materia
    icon: rep_ae2_bridge:matter_foundry
    position: 25
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:matter_foundry
---

# Fonderia di Materia

<Row gap="20">
<BlockImage id="rep_ae2_bridge:matter_foundry" scale="8"></BlockImage>
</Row>

La Fonderia di Materia produce leghe e processori speciali usando **item in input** e **materia** dalla rete Replication (oppure item inviati da AE2).

## Energia

- Preleva **AE** dalla griglia ME e/o **FE** dai cavi in un buffer interno
- Il costo energetico totale è per craft; le Acceleration Card alzano il consumo al tick e accorciano il ciclo

## Funzionamento

- Fino a cinque input e un output
- I requisiti di materia compaiono in GUI come disponibile/richiesto
- Collega un Matter Pipe (o la rete Replication) per drenare i tank
