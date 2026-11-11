# CabecasPvP

Plugin Paper 26.3 / Java 25.

## Comportamento
- Quando um jogador mata outro jogador, a vítima dropa uma cabeça de jogador.
- A cabeça usa a skin da vítima.
- O nome do item identifica a vítima.
- A lore registra `Assassinado por: NOME_DO_ASSASSINO`.
- Mortes sem um jogador responsável não geram cabeça.
- Os drops normais da morte são preservados.

## Build
Execute `gradle build` nesta pasta. O JAR será gerado em `build/libs/CabecasPvP-1.0.1.jar`.
