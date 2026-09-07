export const LANGS = ["en-US", "pt-BR"] as const;

export type Lang = (typeof LANGS)[number];

export const strings: Record<string, Record<Lang, string>> = {
  loading: { "en-US": "loading…", "pt-BR": "carregando…" },
  loadFailed: {
    "en-US": "could not load the session",
    "pt-BR": "não foi possível carregar a sessão",
  },
  retry: { "en-US": "retry", "pt-BR": "tentar de novo" },
  dismiss: { "en-US": "dismiss", "pt-BR": "fechar" },
  appCrashed: {
    "en-US": "the app hit an unexpected error",
    "pt-BR": "o app encontrou um erro inesperado",
  },
  reloadApp: { "en-US": "reload", "pt-BR": "recarregar" },
  emptyCanvas: {
    "en-US": "nothing on the canvas yet",
    "pt-BR": "nada no canvas ainda",
  },
  unknownVariant: {
    "en-US": "unsupported combination",
    "pt-BR": "combinação não suportada",
  },
  "port.LEFT": { "en-US": "LEFT", "pt-BR": "ESQ" },
  "port.RIGHT": { "en-US": "RIGHT", "pt-BR": "DIR" },
  theme: { "en-US": "Theme", "pt-BR": "Tema" },
  "theme.light": { "en-US": "Light", "pt-BR": "Claro" },
  "theme.rose": { "en-US": "Rosé", "pt-BR": "Rosé" },
  "theme.dark": { "en-US": "Dark", "pt-BR": "Escuro" },
  "theme.midnight": { "en-US": "Midnight", "pt-BR": "Meia-noite" },
  language: { "en-US": "Language", "pt-BR": "Idioma" },
  newCanvas: { "en-US": "new session", "pt-BR": "nova sessão" },
  mainMenu: { "en-US": "main menu", "pt-BR": "menu principal" },
  untitled: { "en-US": "untitled", "pt-BR": "sem título" },
  needsFolder: {
    "en-US": "choose a sessions folder first",
    "pt-BR": "escolha uma pasta de sessões primeiro",
  },
  tables: { "en-US": "Tables", "pt-BR": "Tabelas" },
  addScanTooltip: {
    "en-US": "read this table into the canvas",
    "pt-BR": "ler esta tabela para o canvas",
  },
  stepsLabel: { "en-US": "{n} steps", "pt-BR": "{n} passos" },
  hidePalette: {
    "en-US": "hide the operators",
    "pt-BR": "esconder os operadores",
  },
  showPalette: {
    "en-US": "show the operators",
    "pt-BR": "mostrar os operadores",
  },
  resizePalette: {
    "en-US": "drag to resize",
    "pt-BR": "arraste para redimensionar",
  },
  settings: { "en-US": "Settings", "pt-BR": "Configurações" },
  close: { "en-US": "close", "pt-BR": "fechar" },
  captions: { "en-US": "Node labels", "pt-BR": "Rótulos dos nós" },
  showEngineClass: { "en-US": "engine class", "pt-BR": "classe da engine" },
  showExpression: {
    "en-US": "what the node does",
    "pt-BR": "o que o nó faz",
  },
  showPaletteSetting: {
    "en-US": "show the operator palette",
    "pt-BR": "mostrar a paleta de operadores",
  },
  launcherChooseDirIntro: {
    "en-US": "Choose a folder where DBest will keep your session files.",
    "pt-BR":
      "Escolha uma pasta onde o DBest vai guardar seus arquivos de sessão.",
  },
  launcherChooseDir: {
    "en-US": "Choose folder…",
    "pt-BR": "Escolher pasta…",
  },
  launcherChangeDir: { "en-US": "Change folder…", "pt-BR": "Trocar pasta…" },
  launcherStartFresh: {
    "en-US": "Start a new session",
    "pt-BR": "Começar uma sessão nova",
  },
  launcherDirLabel: { "en-US": "Folder: {dir}", "pt-BR": "Pasta: {dir}" },
  launcherOpenSaved: {
    "en-US": "Open a saved session",
    "pt-BR": "Abrir uma sessão salva",
  },
  launcherNoFiles: {
    "en-US": "No saved sessions in this folder yet.",
    "pt-BR": "Ainda não há sessões salvas nesta pasta.",
  },
  nameSession: { "en-US": "Name this session", "pt-BR": "Nome desta sessão" },
  namePlaceholder: { "en-US": "e.g. orders", "pt-BR": "ex.: pedidos" },
  create: { "en-US": "create", "pt-BR": "criar" },
  nameTaken: {
    "en-US": "a session with this name exists — it will be replaced",
    "pt-BR": "já existe uma sessão com este nome — ela será substituída",
  },
  nameAlias: {
    "en-US": "Name this table's alias",
    "pt-BR": "Nomeie o alias desta tabela",
  },
  renameAlias: {
    "en-US": "Rename this table's alias",
    "pt-BR": "Renomeie o alias desta tabela",
  },
  aliasPlaceholder: { "en-US": "e.g. o", "pt-BR": "ex.: p" },
  noTables: { "en-US": "no tables yet", "pt-BR": "nenhuma tabela ainda" },
  newTable: { "en-US": "new table", "pt-BR": "nova tabela" },
  importTable: { "en-US": "import", "pt-BR": "importar" },
  chooseFile: { "en-US": "choose a file…", "pt-BR": "escolher arquivo…" },
  tableName: { "en-US": "name", "pt-BR": "nome" },
  columns: { "en-US": "columns", "pt-BR": "colunas" },
  columnName: { "en-US": "column", "pt-BR": "coluna" },
  primaryKey: { "en-US": "key", "pt-BR": "chave" },
  addColumn: { "en-US": "+ column", "pt-BR": "+ coluna" },
  rows: { "en-US": "rows", "pt-BR": "linhas" },
  rootElement: { "en-US": "root element", "pt-BR": "elemento raiz" },
  recordElement: { "en-US": "record element", "pt-BR": "elemento registro" },
  separator: { "en-US": "separator", "pt-BR": "separador" },
  headerLine: { "en-US": "header line", "pt-BR": "linha do cabeçalho" },
  preview: { "en-US": "preview", "pt-BR": "prévia" },
  run: { "en-US": "run", "pt-BR": "rodar" },
  results: { "en-US": "results", "pt-BR": "resultados" },
  elapsed: { "en-US": "{ms} ms", "pt-BR": "{ms} ms" },
  previous: { "en-US": "previous", "pt-BR": "anterior" },
  next: { "en-US": "next", "pt-BR": "próxima" },
  rowTotal: { "en-US": "{n} tuples", "pt-BR": "{n} tuplas" },
  rowTotalPartial: { "en-US": "{n}? tuples", "pt-BR": "{n}? tuplas" },
  currentPage: { "en-US": "page {n}", "pt-BR": "página {n}" },
  firstPage: { "en-US": "first page", "pt-BR": "primeira página" },
  allRows: { "en-US": "all tuples", "pt-BR": "todas as tuplas" },
  editTitle: { "en-US": "edit {op}", "pt-BR": "editar {op}" },
  save: { "en-US": "save", "pt-BR": "salvar" },
  exit: { "en-US": "exit", "pt-BR": "sair" },
  confirmExit: {
    "en-US": "Are you sure you want to exit? The server will shut down.",
    "pt-BR": "Tem certeza que quer sair? O servidor vai encerrar.",
  },
  exited: {
    "en-US": "DBest has shut down. You can close this tab.",
    "pt-BR": "O DBest foi encerrado. Você pode fechar esta aba.",
  },
  edit: { "en-US": "edit", "pt-BR": "editar" },
  addRow: { "en-US": "+ add", "pt-BR": "+ adicionar" },
  addCondition: { "en-US": "+ condition", "pt-BR": "+ condição" },
  "shape.cmp": { "en-US": "compare", "pt-BR": "compara" },
  "shape.isNull": { "en-US": "is null", "pt-BR": "é nulo" },
  "shape.isNotNull": { "en-US": "is not null", "pt-BR": "não é nulo" },
  "shape.and": { "en-US": "and", "pt-BR": "e" },
  "shape.or": { "en-US": "or", "pt-BR": "ou" },
  "enum.EQ": { "en-US": "=", "pt-BR": "=" },
  "enum.NEQ": { "en-US": "≠", "pt-BR": "≠" },
  "enum.GT": { "en-US": ">", "pt-BR": ">" },
  "enum.GTE": { "en-US": "≥", "pt-BR": "≥" },
  "enum.LT": { "en-US": "<", "pt-BR": "<" },
  "enum.LTE": { "en-US": "≤", "pt-BR": "≤" },
  link: { "en-US": "link", "pt-BR": "ligar" },
  delete: { "en-US": "delete", "pt-BR": "excluir" },
  cancel: { "en-US": "cancel", "pt-BR": "cancelar" },
  undo: { "en-US": "undo", "pt-BR": "desfazer" },
  redo: { "en-US": "redo", "pt-BR": "refazer" },
  linkHint: {
    "en-US": "click a highlighted node to link",
    "pt-BR": "clique num nó destacado para ligar",
  },
  linkWhichInput: { "en-US": "which input?", "pt-BR": "qual entrada?" },
  "link.sameNode": {
    "en-US": "a node cannot feed itself",
    "pt-BR": "um nó não pode alimentar a si mesmo",
  },
  "link.bothFull": {
    "en-US": "both nodes already have every input connected",
    "pt-BR": "os dois nós já têm todas as entradas ligadas",
  },
  "link.cycle": {
    "en-US": "that would make the data flow in a loop",
    "pt-BR": "isso faria os dados circularem em laço",
  },
  "category.Algebra": { "en-US": "Algebra", "pt-BR": "Álgebra" },
  "category.Aggregation": { "en-US": "Aggregation", "pt-BR": "Agregação" },
  "category.ETL": { "en-US": "ETL", "pt-BR": "ETL" },
  "category.Index": { "en-US": "Index", "pt-BR": "Índice" },
  "category.Joins": { "en-US": "Joins", "pt-BR": "Junções" },
  "category.SemiAntiJoins": {
    "en-US": "Semi / Anti joins",
    "pt-BR": "Semi / Anti junções",
  },
  "category.Sets": { "en-US": "Sets", "pt-BR": "Conjuntos" },
  "category.Boolean": { "en-US": "Boolean", "pt-BR": "Booleanos" },
  "op.aggregation": { "en-US": "Aggregation", "pt-BR": "Agregação" },
  "opDesc.aggregation": {
    "en-US": "Groups tuples and computes aggregate functions (sum, count, …).",
    "pt-BR":
      "Agrupa tuplas e calcula funções de agregação (soma, contagem, …).",
  },
  "op.antiJoin": { "en-US": "Anti join", "pt-BR": "Anti-junção" },
  "opDesc.antiJoin": {
    "en-US": "Keeps tuples from one side that have no match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que não possuem correspondência no outro.",
  },
  "op.append": { "en-US": "Append", "pt-BR": "Anexar" },
  "opDesc.append": {
    "en-US": "Concatenates two relations, keeping duplicates.",
    "pt-BR": "Concatena duas relações, mantendo duplicatas.",
  },
  "op.autoInc": { "en-US": "Row number", "pt-BR": "Número da linha" },
  "opDesc.autoInc": {
    "en-US": "Adds a sequential number column to each tuple.",
    "pt-BR": "Adiciona a cada tupla uma coluna com número sequencial.",
  },
  "op.bilateralExistence": {
    "en-US": "Bilateral existence",
    "pt-BR": "Existência bilateral",
  },
  "opDesc.bilateralExistence": {
    "en-US":
      "Filters one relation by the (non-)existence of matches in another.",
    "pt-BR":
      "Filtra uma relação pela (in)existência de correspondências em outra.",
  },
  "op.cartesianProduct": {
    "en-US": "Cartesian product",
    "pt-BR": "Produto cartesiano",
  },
  "opDesc.cartesianProduct": {
    "en-US": "Every combination of tuples from both inputs.",
    "pt-BR": "Todas as combinações de tuplas das duas entradas.",
  },
  "op.collapse": { "en-US": "Collapse", "pt-BR": "Colapsar" },
  "opDesc.collapse": {
    "en-US": "Unifies every source under a single alias.",
    "pt-BR": "Unifica todas as fontes sob um único alias.",
  },
  "op.difference": { "en-US": "Difference", "pt-BR": "Diferença" },
  "opDesc.difference": {
    "en-US": "Tuples in the first relation but not the second.",
    "pt-BR": "Tuplas da primeira relação que não estão na segunda.",
  },
  "op.duplicateRemoval": {
    "en-US": "Duplicate removal",
    "pt-BR": "Remoção de duplicatas",
  },
  "opDesc.duplicateRemoval": {
    "en-US": "Removes duplicate tuples.",
    "pt-BR": "Remove tuplas duplicadas.",
  },
  "op.explode": { "en-US": "Explode", "pt-BR": "Explode" },
  "opDesc.explode": {
    "en-US": "Expands a delimited column into one tuple per value.",
    "pt-BR": "Expande uma coluna delimitada em uma tupla por valor.",
  },
  "op.filter": { "en-US": "Filter", "pt-BR": "Filtro" },
  "opDesc.filter": {
    "en-US": "Keeps the tuples that satisfy a predicate.",
    "pt-BR": "Mantém as tuplas que satisfazem um predicado.",
  },
  "op.hash": { "en-US": "Hash index", "pt-BR": "Índice hash" },
  "opDesc.hash": {
    "en-US": "Builds a hash index over the input.",
    "pt-BR": "Constrói um índice hash sobre a entrada.",
  },
  "op.hashDifference": {
    "en-US": "Hash difference",
    "pt-BR": "Diferença hash",
  },
  "opDesc.hashDifference": {
    "en-US": "Tuples in the first relation but not the second.",
    "pt-BR": "Tuplas da primeira relação que não estão na segunda.",
  },
  "op.hashDuplicateRemoval": {
    "en-US": "Hash duplicate removal",
    "pt-BR": "Remoção de duplicatas hash",
  },
  "opDesc.hashDuplicateRemoval": {
    "en-US": "Removes duplicate tuples.",
    "pt-BR": "Remove tuplas duplicadas.",
  },
  "op.hashFullOuterJoin": {
    "en-US": "Hash full outer join",
    "pt-BR": "Junção externa completa hash",
  },
  "opDesc.hashFullOuterJoin": {
    "en-US":
      "Join keeping tuples from both sides; unmatched columns become null.",
    "pt-BR":
      "Junção que mantém tuplas dos dois lados; colunas sem correspondência ficam nulas.",
  },
  "op.hashIntersection": {
    "en-US": "Hash intersection",
    "pt-BR": "Interseção hash",
  },
  "opDesc.hashIntersection": {
    "en-US": "Tuples present in both relations.",
    "pt-BR": "Tuplas presentes nas duas relações.",
  },
  "op.hashJoin": { "en-US": "Hash join", "pt-BR": "Junção hash" },
  "opDesc.hashJoin": {
    "en-US": "Combines tuples that satisfy the join predicate.",
    "pt-BR": "Combina as tuplas que satisfazem o predicado de junção.",
  },
  "op.hashLeftAntiJoin": {
    "en-US": "Hash left anti join",
    "pt-BR": "Anti-junção à esquerda hash",
  },
  "opDesc.hashLeftAntiJoin": {
    "en-US": "Keeps tuples from one side that have no match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que não possuem correspondência no outro.",
  },
  "op.hashLeftOuterJoin": {
    "en-US": "Hash left outer join",
    "pt-BR": "Junção externa à esquerda hash",
  },
  "opDesc.hashLeftOuterJoin": {
    "en-US":
      "Join keeping all left tuples; unmatched right columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da esquerda; colunas da direita sem correspondência ficam nulas.",
  },
  "op.hashLeftSemiJoin": {
    "en-US": "Hash left semi join",
    "pt-BR": "Semi-junção à esquerda hash",
  },
  "opDesc.hashLeftSemiJoin": {
    "en-US": "Keeps tuples from one side that have a match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que possuem correspondência no outro.",
  },
  "op.hashRightAntiJoin": {
    "en-US": "Hash right anti join",
    "pt-BR": "Anti-junção à direita hash",
  },
  "opDesc.hashRightAntiJoin": {
    "en-US": "Keeps tuples from one side that have no match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que não possuem correspondência no outro.",
  },
  "op.hashRightOuterJoin": {
    "en-US": "Hash right outer join",
    "pt-BR": "Junção externa à direita hash",
  },
  "opDesc.hashRightOuterJoin": {
    "en-US":
      "Join keeping all right tuples; unmatched left columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da direita; colunas da esquerda sem correspondência ficam nulas.",
  },
  "op.hashRightSemiJoin": {
    "en-US": "Hash right semi join",
    "pt-BR": "Semi-junção à direita hash",
  },
  "opDesc.hashRightSemiJoin": {
    "en-US": "Keeps tuples from one side that have a match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que possuem correspondência no outro.",
  },
  "op.hashUnion": { "en-US": "Hash union", "pt-BR": "União hash" },
  "opDesc.hashUnion": {
    "en-US":
      "Combines tuples from two compatible relations, removing duplicates.",
    "pt-BR":
      "Combina tuplas de duas relações compatíveis, removendo duplicatas.",
  },
  "op.intersection": { "en-US": "Intersection", "pt-BR": "Interseção" },
  "opDesc.intersection": {
    "en-US": "Tuples present in both relations.",
    "pt-BR": "Tuplas presentes nas duas relações.",
  },
  "op.join": { "en-US": "Join", "pt-BR": "Junção" },
  "opDesc.join": {
    "en-US": "Combines tuples that satisfy the join predicate.",
    "pt-BR": "Combina as tuplas que satisfazem o predicado de junção.",
  },
  "op.leftOuterJoin": {
    "en-US": "Left outer join",
    "pt-BR": "Junção externa à esquerda",
  },
  "opDesc.leftOuterJoin": {
    "en-US":
      "Join keeping all left tuples; unmatched right columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da esquerda; colunas da direita sem correspondência ficam nulas.",
  },
  "op.limit": { "en-US": "Limit", "pt-BR": "Limite" },
  "opDesc.limit": {
    "en-US": "Keeps at most N tuples, optionally skipping the first ones.",
    "pt-BR": "Mantém no máximo N tuplas, opcionalmente pulando as primeiras.",
  },
  "op.logicalAnd": { "en-US": "And", "pt-BR": "E" },
  "opDesc.logicalAnd": {
    "en-US": "Emits a single boolean row: true when both sides yield rows.",
    "pt-BR":
      "Emite uma única linha booleana: verdadeira quando os dois lados têm linhas.",
  },
  "op.logicalOr": { "en-US": "Or", "pt-BR": "Ou" },
  "opDesc.logicalOr": {
    "en-US": "Emits a single boolean row: true when either side yields rows.",
    "pt-BR":
      "Emite uma única linha booleana: verdadeira quando algum dos lados tem linhas.",
  },
  "op.logicalXor": { "en-US": "Xor", "pt-BR": "Xor" },
  "opDesc.logicalXor": {
    "en-US":
      "Emits a single boolean row: the exclusive-or of the two sides' conditions.",
    "pt-BR":
      "Emite uma única linha booleana: o ou-exclusivo das condições dos dois lados.",
  },
  "op.materialization": { "en-US": "Materialize", "pt-BR": "Materializar" },
  "opDesc.materialization": {
    "en-US": "Materializes the input into a temporary relation.",
    "pt-BR": "Materializa a entrada em uma relação temporária.",
  },
  "op.memoize": { "en-US": "Memoize", "pt-BR": "Memoize" },
  "opDesc.memoize": {
    "en-US": "Caches the input's tuples for reuse.",
    "pt-BR": "Armazena as tuplas da entrada em cache para reutilização.",
  },
  "op.mergeFullOuterJoin": {
    "en-US": "Merge full outer join",
    "pt-BR": "Junção externa completa merge",
  },
  "opDesc.mergeFullOuterJoin": {
    "en-US":
      "Join keeping tuples from both sides; unmatched columns become null.",
    "pt-BR":
      "Junção que mantém tuplas dos dois lados; colunas sem correspondência ficam nulas.",
  },
  "op.mergeJoin": { "en-US": "Merge join", "pt-BR": "Junção merge" },
  "opDesc.mergeJoin": {
    "en-US": "Combines tuples that satisfy the join predicate.",
    "pt-BR": "Combina as tuplas que satisfazem o predicado de junção.",
  },
  "op.mergeLeftAntiJoin": {
    "en-US": "Merge left anti join",
    "pt-BR": "Anti-junção à esquerda merge",
  },
  "opDesc.mergeLeftAntiJoin": {
    "en-US": "Keeps tuples from one side that have no match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que não possuem correspondência no outro.",
  },
  "op.mergeLeftOuterJoin": {
    "en-US": "Merge left outer join",
    "pt-BR": "Junção externa à esquerda merge",
  },
  "opDesc.mergeLeftOuterJoin": {
    "en-US":
      "Join keeping all left tuples; unmatched right columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da esquerda; colunas da direita sem correspondência ficam nulas.",
  },
  "op.mergeLeftSemiJoin": {
    "en-US": "Merge left semi join",
    "pt-BR": "Semi-junção à esquerda merge",
  },
  "opDesc.mergeLeftSemiJoin": {
    "en-US": "Keeps tuples from one side that have a match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que possuem correspondência no outro.",
  },
  "op.mergeRightAntiJoin": {
    "en-US": "Merge right anti join",
    "pt-BR": "Anti-junção à direita merge",
  },
  "opDesc.mergeRightAntiJoin": {
    "en-US": "Keeps tuples from one side that have no match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que não possuem correspondência no outro.",
  },
  "op.mergeRightOuterJoin": {
    "en-US": "Merge right outer join",
    "pt-BR": "Junção externa à direita merge",
  },
  "opDesc.mergeRightOuterJoin": {
    "en-US":
      "Join keeping all right tuples; unmatched left columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da direita; colunas da esquerda sem correspondência ficam nulas.",
  },
  "op.mergeRightSemiJoin": {
    "en-US": "Merge right semi join",
    "pt-BR": "Semi-junção à direita merge",
  },
  "opDesc.mergeRightSemiJoin": {
    "en-US": "Keeps tuples from one side that have a match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que possuem correspondência no outro.",
  },
  "op.projection": { "en-US": "Projection", "pt-BR": "Projeção" },
  "opDesc.projection": {
    "en-US": "Keeps only the named columns, dropping the rest.",
    "pt-BR": "Mantém apenas as colunas indicadas, descartando as demais.",
  },
  "op.rename": { "en-US": "Rename", "pt-BR": "Renomear" },
  "opDesc.rename": {
    "en-US": "Renames a column.",
    "pt-BR": "Renomeia uma coluna.",
  },
  "op.rightOuterJoin": {
    "en-US": "Right outer join",
    "pt-BR": "Junção externa à direita",
  },
  "opDesc.rightOuterJoin": {
    "en-US":
      "Join keeping all right tuples; unmatched left columns become null.",
    "pt-BR":
      "Junção que mantém todas as tuplas da direita; colunas da esquerda sem correspondência ficam nulas.",
  },
  "op.scan": { "en-US": "Scan", "pt-BR": "Varredura" },
  "opDesc.scan": {
    "en-US": "Forces a full scan of the input, preventing an index lookup.",
    "pt-BR":
      "Força uma varredura completa da entrada, impedindo a busca por chave.",
  },
  "op.selectColumns": {
    "en-US": "Select columns",
    "pt-BR": "Selecionar colunas",
  },
  "opDesc.selectColumns": {
    "en-US": "Selects a subset of columns, keeping their order.",
    "pt-BR": "Seleciona um subconjunto de colunas, preservando a ordem.",
  },
  "op.semiJoin": { "en-US": "Semi join", "pt-BR": "Semi-junção" },
  "opDesc.semiJoin": {
    "en-US": "Keeps tuples from one side that have a match on the other.",
    "pt-BR":
      "Mantém as tuplas de um lado que possuem correspondência no outro.",
  },
  "op.sort": { "en-US": "Sort", "pt-BR": "Ordenação" },
  "opDesc.sort": {
    "en-US": "Reorders tuples by one or more columns.",
    "pt-BR": "Reordena as tuplas por uma ou mais colunas.",
  },
  "op.unilateralExistence": {
    "en-US": "Unilateral existence",
    "pt-BR": "Existência unilateral",
  },
  "opDesc.unilateralExistence": {
    "en-US":
      "Filters one relation by the (non-)existence of matches in another.",
    "pt-BR":
      "Filtra uma relação pela (in)existência de correspondências em outra.",
  },
  "op.union": { "en-US": "Union", "pt-BR": "União" },
  "opDesc.union": {
    "en-US":
      "Combines tuples from two compatible relations, removing duplicates.",
    "pt-BR":
      "Combina tuplas de duas relações compatíveis, removendo duplicatas.",
  },
};
