# PuraArena

Plugin de minigames PvP para Paper 26.3, com fila Gladiador, X1 por convite, kits selecionaveis, espectadores e restauracao do estado dos jogadores.

## Arenas

- Cria e reutiliza seis arenas em um mundo separado chamado `puraarena_world`.
- O mundo e plano e as arenas ficam isoladas do mundo survival.
- Cada arena tem piso, paredes e teto de barreira para impedir fugas com Elytra.
- O Gladiador escolhe aleatoriamente uma arena livre.
- Se todas estiverem ocupadas, os jogadores permanecem na fila.
- O mapa e protegido: jogadores podem colocar e quebrar teias dentro da propria arena, mas nao podem alterar a estrutura.
- Explosoes nao destroem os mapas e criaturas nao aparecem dentro das arenas.
- Perolas do Ender funcionam dentro da arena da partida. Tentativas de sair sao bloqueadas e a perola e devolvida.

## Partidas e recuperacao

- X1 por convite com expiracao configuravel.
- Desistencia por `/l` ou pela cama vermelha com confirmacao.
- Morte exibe vitoria/derrota e permite espectar, pedir revanche ou voltar.
- Desconexao encerra a partida e concede a vitoria ao adversario.
- Inventario, armadura, mao secundaria, local, vida, fome, experiencia, modo de jogo, efeitos de pocao e configuracoes de voo sao guardados e restaurados.
- Dados de recuperacao sao persistidos no arquivo de configuracao para recuperacao apos reinicio/crash.
- Replay foi removido.

## Kits

- **PvP:** netherite, espada, machado, arco, escudo, maçãs douradas normais, perolas, teias, agua, blocos, comida e pocoes.
- **Mace:** kit PvP, Mace com Density V, Wind Burst III, Unbreaking III e Mending, Elytra, foguetes e Wind Charges.
- **Spear:** kit PvP e Spear com Lunge III, Sharpness V, Unbreaking III e Mending. Em builds sem o item Spear, usa Tridente como fallback.
- **Mace + Spear:** combina os dois equipamentos especiais.
- **Classico:** armadura de ferro, espada, machado, escudo, maçãs douradas normais, perolas e teias.

Maçãs douradas encantadas não são incluídas.

## Comandos

- `/gladiador` ou `/glad` - abre o menu.
- `/gladiador entrar` - entra na fila.
- `/gladiador sair` - sai da fila.
- `/x1 jogador` - envia convite.
- `/x1 aceitar` - aceita convite.
- `/x1 recusar` - recusa convite.
- `/x1 cancelar` - cancela convite enviado.
- `/x1 rematch` - pede revanche durante a tela final.
- `/x1 lobby` - volta ao lobby após a partida.
- `/spec [jogador]` - assiste uma partida ativa.
- `/spec sair` - encerra o espectador.
- `/l` - desiste da partida.
- `/arena top` - mostra as vitórias da sessão atual.

## Configuracao

Arquivo: `plugins/PuraArena/config.yml`

- `arena.world`: mundo dedicado.
- `arena.pool-size`: quantidade de arenas (minimo 6).
- `arena.spacing`: distancia entre arenas.
- `invitation-timeout-seconds`: duracao dos convites.
- `spectator-seconds`: tempo da fase final apos a morte.

O workflow **Build PuraArena** compila com Java 25 e publica o JAR como artifact. A compilacao automatizada nao substitui os testes dentro do servidor Paper real.
