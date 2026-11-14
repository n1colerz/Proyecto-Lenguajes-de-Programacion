const path = require('node:path');
const { loadOrders } = require('./storage');
const { OrderService } = require('./service');

async function main() {
  const datasets = ['solicitudes_pequenas', 'solicitudes_medias', 'solicitudes_grandes'];
  const datasetIndex = process.argv.indexOf('--dataset');
  const dataset = datasetIndex >= 0 ? process.argv[datasetIndex + 1] : datasets[0];
  const techniciansIndex = process.argv.indexOf('--tecnicos');
  const technicians = techniciansIndex >= 0 ? Number(process.argv[techniciansIndex + 1]) : 4;
  const modeIndex = process.argv.indexOf('--modo');
  const mode = modeIndex >= 0 ? process.argv[modeIndex + 1] : 'concurrente';
  if (!datasets.includes(dataset)) throw new TypeError(`Dataset desconocido: ${dataset}`);
  if (!['secuencial', 'concurrente'].includes(mode)) throw new TypeError(`Modo desconocido: ${mode}`);

  const projectRoot = path.resolve(__dirname, '../..');
  const orders = await loadOrders(path.join(projectRoot, 'datos', `${dataset}.json`));
  const processor = mode === 'secuencial'
    ? OrderService.processSequentially
    : OrderService.processConcurrently;
  const processing = await processor.call(OrderService, orders, technicians);
  console.log(JSON.stringify({ resumen: OrderService.salesSummary(orders), procesamiento: processing }, null, 2));
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

module.exports = { main };
