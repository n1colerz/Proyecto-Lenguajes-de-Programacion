const fs = require('node:fs/promises');
const { Order } = require('./model');

async function loadOrders(filePath) {
  try {
    const records = JSON.parse(await fs.readFile(filePath, 'utf8'));
    if (!Array.isArray(records)) throw new TypeError('El archivo JSON debe contener una lista de pedidos.');
    return records.map((record) => new Order(record));
  } catch (error) {
    throw new Error(`No se pudieron leer los pedidos de ${filePath}: ${error.message}`, { cause: error });
  }
}

async function saveOrders(filePath, orders) {
  try {
    await fs.writeFile(filePath, `${JSON.stringify(orders, null, 2)}\n`, 'utf8');
  } catch (error) {
    throw new Error(`No se pudieron guardar los pedidos en ${filePath}: ${error.message}`, { cause: error });
  }
}

module.exports = { loadOrders, saveOrders };
