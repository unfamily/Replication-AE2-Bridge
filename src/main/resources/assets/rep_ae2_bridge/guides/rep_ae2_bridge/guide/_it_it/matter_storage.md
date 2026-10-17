---
navigation:
    parent: index.md
    title: Storage di Materia
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

# Storage di Materia

<Row gap="20">
<ItemImage id="rep_ae2_bridge:matter_storage_cell_1k" scale="4"></ItemImage>
<ItemImage id="rep_ae2_bridge:matter_storage_cell_4k" scale="4"></ItemImage>
<ItemImage id="rep_ae2_bridge:matter_storage_cell_16k" scale="4"></ItemImage>
</Row>

Le celle di materia tengono la materia di Replication nel **sistema ME**, come le celle normali tengono gli item. Con un Bridge collegato, quella materia è disponibile anche alla rete di materia.

## Celle e involucro

- Si craftano con un **Involucro Cella di Materia** e il component AE2 corrispondente (1k / 4k / 16k / 64k / 256k)
- La capacità segue quel component
- Si può craftare anche in un passo solo, con il pattern dell’involucro e il component al centro
- La **Cella di Materia Creativa** dà quantità illimitate di ogni tipo (creativa / test)

Metti le celle nei drive ME come le altre. Nei terminali la materia si filtra come tipo separato da item e fluidi.

## Import, export e storage bus

Con questo addon, **Import Bus**, **Export Bus** e **Storage Bus** di AE2 possono muovere materia verso **matter tank** e **Disintegrator** di Replication adiacenti. Abilita il tipo Matter sul bus come per i fluidi.

Uno **Storage Bus** su una matter tank **elenca** i contenuti in ME e può inserire o estrarre materia (bidirezionale). Import Bus tira tank → ME; Export Bus spinge ME → tank.

## Pattern di disintegrazione (item → materia)

Sul **Pattern Encoding Terminal**, usa la scheda **Matter** (icona blueprint):

- Metti un item in input (aumenta la quantità per scalare gli output)
- Il pannello mostra l’anteprima della materia (non selezionabile)
- Encode crea un normale **processing pattern** AE2

Metti il pattern in un **Pattern Provider** davanti a un **Disintegrator**. Recupera la materia con un **Import Bus** (o Storage Bus) sul Disintegrator. Il Bridge rispecchia anche la materia della rete Replication in ME come stock.

## Item matter JEI

Gli item matter virtuali di questo addon servono per JEI. Trascinandoli nei filtri o monitor AE2 configurano automaticamente la vera chiave **Matter**.

## Storage monitor

Click destro su uno **Storage Monitor** sbloccato tenendo un **Matter Tank** o un item matter virtuale:

- Tank pieno / item matter → quel tipo di materia
- Tank vuoto su monitor vuoto → AE2 configura l’item tank
- Tank vuoto con qualcosa già impostato → prima materia, poi ciclo

## Autocraft e visibilità della materia

Con un Bridge, i craft AE2 prenotano la materia senza toglierla da ME. Lo stock resta in celle / tank / bus così Replication la vede ancora; il drain reale avviene quando parte il pattern. Annullare toglie solo la prenotazione phantom e non duplica materia.

## Consigli

- La materia vista dal Bridge e quella nelle celle è lo stesso pool per le macchine Replication che usano il Bridge
- Per l’autocraft che produce materia serve Import/Storage Bus sul Disintegrator; lo specchio del Bridge mostra lo stock ma non completa da solo l’output del Pattern Provider
- Lascia spazio nelle celle se sposti molta materia via AE2
