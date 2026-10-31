---
navigation:
    parent: index.md
    title: Ponte AE2–Replication
    icon: rep_ae2_bridge:rep_ae2_bridge
    position: 10
categories:
- rep_bridge_components
item_ids:
- rep_ae2_bridge:rep_ae2_bridge
---

# Ponte AE2–Replication

<Row gap="20">
<BlockImage id="rep_ae2_bridge:rep_ae2_bridge" scale="8"></BlockImage>
</Row>

Il Ponte è il collegamento tra la tua **rete ME** e una **rete di materia Replication**. Posizionalo dove entrambe possono raggiungerlo: un cavo ME da un lato, un tubo di materia dall’altro.

## Cosa fa

- Mostra la materia Replication nei terminali AE2 (come tipo materia dedicato)
- Permette all’autocrafting AE2 di richiedere oggetti tramite Replication
- Può alimentare la rete di materia con potenza AE libera quando attivi l’opzione (rispetta il ratio FE di AE2; si ferma se la rete ME è in sofferenza energetica)
- Accetta gli oggetti finiti da Replicator e Assemblatori di Materia e li mette nel sistema ME quando c’è spazio

## Interfaccia

Clic destro sul Ponte per aprire la schermata.

- **Alimenta rete di materia** — interruttore che invia potenza AE libera alla rete di materia come FE (dinamico; usa il ratio FE di AE2)
- **Priorità** — stessa scheda priorità delle altre macchine AE2; valori più alti sono preferiti quando la rete sceglie dove far lavorare

Se il sistema ME è momentaneamente pieno, gli oggetti finiti possono restare nel buffer del Ponte e passare allo storage quando c’è di nuovo spazio.

## Materia in autocraft

Quando AE2 avvia un craft Replication, la materia resta nelle celle / tank / Storage Bus (la CPU tiene solo una prenotazione phantom). Replication continua a vedere quello stock finché il pattern non parte e non la drena. Annullare un craft non duplica la materia.

## Consigli

- Di solito basta un Ponte per rete di materia
- Tieni il Ponte online sia sulla rete ME sia sui tubi di materia
- Abbinalo agli Assemblatori di Materia sulla stessa rete ME per lavori Replication più veloci
