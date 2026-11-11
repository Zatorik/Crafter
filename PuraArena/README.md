# PuraArena

Plugin Paper para Gladiador e X1 particular.

## Arenas

Na primeira inicializacao, o plugin cria o mundo separado `puraarena` (plano) e prepara quatro arenas fechadas, com piso e paredes de vidro. As arenas ficam prontas antes das partidas e sao reutilizadas, evitando construir/destruir estruturas durante cada X1.

Quando uma partida termina, o espaco e liberado para a proxima. O mundo principal nao e alterado. Blocos das arenas ficam protegidos contra quebra, colocacao e explosoes. Se todas as arenas estiverem ocupadas, jogadores do Gladiador aguardam na fila.

Se quiser aumentar a capacidade, ajuste `arena.pool-size` e `arena.spacing` em `plugins/PuraArena/config.yml`. O mundo dedicado deve permanecer instalado. Para forcar a preparacao novamente, pare o servidor e defina `arena.generated: false`.

## Comandos

- `/gladiador` - abre o menu.
- `/gladiador entrar` - entra na fila.
- `/gladiador sair` - sai da fila.
- `/x1 jogador` - envia convite particular.
- `/x1 aceitar` - aceita um convite.
- `/x1 recusar` - recusa um convite.
- `/x1 cancelar` - cancela convite enviado.
- `/arena top` - mostra suas vitorias.

Convites expiram em 60 segundos. O tempo de espectador e configuravel em `spectator-seconds`.

O GitHub Actions compila com Java 25 e publica o JAR como artifact.
