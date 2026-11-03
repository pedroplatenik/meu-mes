# Formato dos dados

Todas as versões do Meu Mês (Android, Windows, iPhone, iPad e Mac) usam o mesmo arquivo JSON. É ele que vai na **cópia de segurança**, então uma cópia feita num aparelho abre em qualquer outro.

## Visão geral

```json
{
  "v": 1,
  "perfil":  { "nome": "Maria", "tema": "verde", "fonte": "g", "ok": true },
  "ganhos":  [ ... ],
  "contas":  [ ... ],
  "meses":   { "2026-11": { "pagos": {}, "valores": {}, "ocultos": {} } },
  "config":  { "lembretes": true, "diasAntes": 1, "noDia": true, "hora": 9, "avisoGanho": true }
}
```

| Campo | O que é |
|---|---|
| `v` | Versão do formato (hoje `1`) |
| `perfil.tema` | `verde`, `azul`, `roxo`, `rosa` ou `laranja` |
| `perfil.fonte` | Tamanho das letras: `n` (normal), `g` (grande), `mg` (bem grande) |
| `perfil.ok` | `true` depois que a pessoa termina as boas-vindas |
| `config` | Lembretes do Android: quantos dias antes, aviso no dia, horário e aviso quando o dinheiro cai |

## Contas e ganhos são "modelos"

Uma conta ou um ganho não é guardado mês a mês: é um **modelo** que se repete, com histórico de valores. O app calcula cada mês a partir dele.

### Campos em comum

| Campo | O que é |
|---|---|
| `id` | Identificador único |
| `nome` | Nome que aparece na tela |
| `variavel` | `true` quando o valor muda todo mês (o valor de cada mês fica em `meses[mês].valores`) |
| `hist` | Histórico de valores fixos: `[{ "desde": "2026-01", "valor": 99.9 }]`. Vale o mais recente cujo `desde` é anterior ou igual ao mês |
| `rec` | `mensal`, `parcelas` (só contas) ou `unica` |
| `inicio` | Primeiro mês (`AAAA-MM`) |
| `fim` | Último mês, ou `null` |

### Só em contas

| Campo | O que é |
|---|---|
| `cat` | Categoria: `luz`, `agua`, `gas`, `internet`, `celular`, `moradia`, `cartao`, `emprestimo`, `saude`, `plano`, `mercado`, `transporte`, `carne`, `assinatura`, `pet`, `doacao`, `educacao`, `imposto`, `seguro`, `outro` |
| `dia` | Dia do vencimento (1 a 31), ou `null` para "ainda não sei" |
| `parcelas` | Total de parcelas quando `rec` é `parcelas`. A parcela do mês é `meses desde o início + 1` |
| `obs` | Observação livre |

### Só em ganhos

| Campo | O que é |
|---|---|
| `tipo` | `aposentadoria`, `pensao`, `salario`, `beneficio`, `aluguel`, `extra`, `vendas`, `ajuda`, `outro` |
| `quando.modo` | `dia` (dia fixo), `util` (N-ésimo dia útil) ou `ultimo` (último dia útil) |
| `quando.n` | O dia fixo, ou o N do dia útil |
| `quando.sab` | Se o sábado conta como dia útil |

Os dias úteis consideram os feriados nacionais (1/1, 21/4, 1/5, 7/9, 12/10, 2/11, 15/11, 20/11 a partir de 2024 e 25/12) e a Sexta-feira Santa.

## O que muda em cada mês

`meses` guarda só o que foge do modelo, por mês (`AAAA-MM`):

| Campo | O que é |
|---|---|
| `pagos` | `{ "id": 1 }` para cada conta paga naquele mês |
| `valores` | `{ "id": 182.4 }` com o valor só daquele mês (contas variáveis, ou um valor diferente num mês) |
| `ocultos` | `{ "id": 1 }` para itens tirados só daquele mês |

Meses sem nada diferente não aparecem no arquivo.

## Na versão web (iPhone, iPad e Mac)

No aparelho, o arquivo acima fica cifrado com a senha da pessoa:

```json
{ "v": 1, "sal": "…", "iter": 310000, "iv": "…", "ct": "…", "salvoEm": "2026-11-03T12:00:00.000Z" }
```

A chave AES-GCM de 256 bits é derivada da senha com PBKDF2-SHA-256 (`iter` iterações, `sal` de 16 bytes). Cada gravação usa um `iv` novo, de 12 bytes. A **cópia de segurança** exportada é o JSON aberto, para poder ser restaurada em qualquer versão.
