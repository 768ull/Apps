# TGCA+ — Two-Game Collection Apps

Aplicativo Android **nativo** que reúne em uma coleção os dois jogos originais do
simulador de telefone do site [algorithomus.com.br](https://algorithomus.com.br/):

- **Cobrinha** (Jogo da Cobrinha)
- **Frutris** (estilo Tetris)

## Características

- **100% nativo**: Kotlin + Jetpack Compose (Canvas). Nenhuma WebView — a lógica
  original dos dois jogos foi portada fielmente para motores Kotlin puros.
- **Tela cheia imersiva**: barra de status e barra de navegação ocultas
  (modo imersivo fixado com foco); os botões do sistema não aparecem no jogo.
- **Ícones originais**: os ícones WebP originais dos dois jogos estão incluídos
  e aparecem na tela inicial da coleção.
- **Botão de casinha** em ambos os jogos para voltar à tela inicial do aplicativo.
- **Salvamento automático** nos dois jogos (estado completo + recordes), retomado
  ao reabrir o aplicativo.
- **Sem permissões**: o aplicativo é totalmente offline e não solicita nenhum
  acesso.
- **Arquitetura extensível**: o catálogo de jogos (`GameCatalog.kt`) foi preparado
  para receber novos jogos no futuro.

## Fidelidade à lógica original

### Cobrinha
Grade 14×14, início com 4 segmentos no centro, passo de 145 ms (−9 ms por nível,
mínimo 72 ms), um nível a cada 5 frutas (máximo 10), +10 pontos por fruta,
contagem regressiva "3… 2… 1… COBRA!", tremor na morte, fila de 2 entradas com
proteção anti-reverso, três temas visuais originais (LCD, Fósforo e Neon),
direcional opcional na tela e controles por toque e deslize.

### Frutris
Grade 10×20 (+4 linhas ocultas), peças I, J, L, O, S, T, Z com as cores exatas do
original, queda 1000 ms × 0,8^(nível−1) (mínimo 100 ms), lock delay de 1 s com
15 reposições, kicks de rotação, pontuação de linhas [100, 300, 500, 800],
T-spin (400 pontos, dobrado se limpar linhas), fila de 5 peças, guarda (hold),
combo, lixo (garbage) por marco de nível e temporizado em níveis altos, meta de
recorde de 30000 pontos ("VOCÊ É O MELHOR!"), animação de fim de jogo linha a
linha, gestos originais (arrasto horizontal, toque para girar, deslize para
baixo = queda brusca, deslize para cima = guardar) e botões com DAS/ARR.

Os efeitos sonoros são sintetizados em tempo real (AudioTrack) com as
frequências dos efeitos originais — sem arquivos de áudio.

## Ícone do aplicativo

Fundo vermelho com a marca "TGCA➕" em amarelo (ícone adaptativo vetorial).

## Como compilar

### Pela GitHub Actions (recomendado)
1. Abra a aba **Actions** deste repositório.
2. Execute o workflow **Build APK** (ou aguarde um push dispará-lo).
3. Baixe o artefato **TGCA-debug-apk** e instale o `app-debug.apk` no telefone
   (ative "instalar apps de fontes desconhecidas" se necessário).

### Localmente
```bash
gradle wrapper        # gera o wrapper (Gradle 8.9)
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Requisitos: JDK 17, Android SDK 35.

## Como adicionar um novo jogo no futuro

1. Crie o motor do jogo (lógica pura, salvamento em JSON) e a tela Compose em
   `app/src/main/java/com/tgca/collection/games/<jogo>/`.
2. Acrescente o destino no enum `Dest` (`GameCatalog.kt`) e a entrada no
   catálogo, apontando para o ícone.
3. Ligue o destino em `MainActivity.TGCAApp()`.

## Créditos

Os dois jogos foram criados originalmente para o simulador de telefone do site
[algorithomus.com.br](https://algorithomus.com.br/). Este projeto é uma coleção
nativa que os reproduz fielmente, com a lógica, os textos, os temas, os ícones e
os sons originais portados para Android.
