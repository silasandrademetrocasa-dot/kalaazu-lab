# Kalaazu LAB — Fase 8 · Ponte HTML5 / WebSocket

Projeto isolado: **não substitui os módulos Java existentes** e não altera o Stellar Legacy.

## O que está pronto

- `client/index.html`: interface espacial da Fase 7 preservada, com botão **Conectar ao servidor**.
- `gateway/main.py`: gateway HTTP e WebSocket (Python/aiohttp) para a porta TCP V4 do Kalaazu.
- `gateway/requirements.txt`: dependência instalada somente na nuvem ou servidor de testes.
- `tests/test_bridge.py`: integração local do protocolo com servidor TCP e API simulados.
- `tests/test_live_java.py`: integração contra o Java e MariaDB reais no GitHub Actions.
- `.github/workflows/lab-bridge.yml`: workflow independente da sequência atual do Universe test.

### Recursos conectados nesta fase

O gateway traduz o `LOGIN` V4 e aguarda o pacote `0|I|...` para enviar `RDY`, respeitando a ordem validada na Fase 6. Em seguida, entrega ao navegador eventos reais da Phoenix, NPCs próximos, caixas, estações, portais e movimentos recebidos. O navegador envia **movimento** e **seleção de alvo** por uma lista restrita de comandos permitidos. A lógica autoritativa permanece no Java.

**Ainda NÃO implementados no modo online:** dano/combate, coleta/recompensas, outras contas simultâneas, persistência real de cliente, câmera de todos os jogadores, navegação entre mapas e operação pública. Esses recursos permanecem apenas demonstrativos no modo offline anterior. O botão de ataque e coleta fica bloqueado ao conectar online. A apresentação gráfica usa vetores de demonstração, não sprites oficiais.

## Testar usando SOMENTE o GitHub (sem instalar apps no PC)

1. Abra a branch `develop` do repositório `silasandrademetrocasa-dot/kalaazu-lab`.
2. Extraia o ZIP usando **Extrair tudo** no Windows (recurso nativo, sem instalar software).
3. Envie os arquivos da pasta extraída para os **mesmos caminhos** do repositório pelo GitHub (Add file / Upload files, ou crie arquivo e cole conteúdo pelo navegador). Os módulos anteriores não precisam ser apagados. Evite subir apenas o ZIP sem extrair: o GitHub Actions não consegue executar arquivos que continuam dentro do ZIP.
4. Na aba **Actions**, execute `Kalaazu LAB - HTML5 bridge test` na branch `develop`.
5. A execução cria um banco MariaDB **temporário**, configura Java 25, inicia o Kalaazu, a ponte HTTP/WebSocket e roda dois testes (simulado e servidor real). O workflow finaliza tudo e encerra os processos.

O HTML pode ser aberto offline com Chrome/Edge. Para conectar a um Java real, ele deve ser servido pelo gateway ativo (e não aberto como `file://`): por exemplo `http://127.0.0.1:8090` dentro da máquina onde roda o gateway. **GitHub Actions não publica esse endereço para a internet**. Um endereço público só será disponibilizado após a futura implantação numa hospedagem permanente (fase 9).

### Para executar o gateway em um servidor de desenvolvimento (não no PC da empresa)

```sh
python -m pip install -r gateway/requirements.txt
BRIDGE_BIND=127.0.0.1 BRIDGE_PORT=8090 \
KALAAZU_HTTP=http://127.0.0.1:8081 \
KALAAZU_GAME_HOST=127.0.0.1 KALAAZU_GAME_PORT=8080 \
python gateway/main.py
```

Depois, abra `http://127.0.0.1:8090` **na mesma máquina**. A porta 8090 não fica publicamente acessível por padrão.

### Segurança e limites conhecidos

- **LAB isolado somente:** a autenticação do jogo é mantida na memória do gateway e vinculada a um cookie `HttpOnly` `SameSite=Strict`. Não é enviada ao JavaScript do cliente.
- Apenas requisições da origem configurada são autorizadas, e o gateway só encaminha comandos de movimento e seleção com limites de tamanho e frequência.
- Binding padrão é `127.0.0.1`. Não exponha este gateway à internet sem TLS, cookies Secure, proteção contra abuso, configuração explícita de origem, gestão real de sessões e revisão de segurança.
- Para hospedar atrás de HTTPS, configure `BRIDGE_PUBLIC_ORIGIN=https://seu-dominio` e `BRIDGE_COOKIE_SECURE=true`, além de um proxy seguro e restrições de rede.
- O cadastro clássico atualmente aceita emails com sufixos de 2–3 letras e nome antes do @ com até 16 caracteres.
- O gateway usa `usersId` quando a API o fornece e, no LAB descartável em que IDs de usuários e contas coincidem, usa o ID da conta como fallback. **Para produção** deve buscar e validar o ID de usuário autoritativo no backend, não usar esse fallback.
- O servidor Kalaazu original imprime identificadores de sessão em logs de depuração; desative/redija esses logs antes de usar contas reais.
- O workflow reconstrói `accounts_settings` **somente no banco descartável do job**. Não copie isso para bancos persistentes com jogadores.
- A Fase 8 não prova operação multiplayer nem um servidor público 24/7; o teste com Java real precisa passar no GitHub Actions.

## Licença e referência

Origem do backend: `https://github.com/manulaiko/Kalaazu` (MIT). Veja `LICENSE-KALAAZU.txt` para a licença e os avisos originais. Cliente vetorial e gateway foram construídos para este laboratório, sem recursos gráficos comerciais de DarkOrbit incluídos no ZIP.
