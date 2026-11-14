const { ORDER_STATES } = require('./model');

const SHIPPING_CENTS = { estandar: 5000, express: 10000, retiro: 0 };
const money = (cents) => cents / 100;
const toCents = (amount) => Math.round((amount + Number.EPSILON) * 100);

class OrderService {
  static subtotalCents(order) {
    return order.products.reduce((sum, product) => sum + toCents(product.price) * product.quantity, 0);
  }

  static discountCents(order) {
    const subtotal = this.subtotalCents(order);
    const rate = order.customerType === 'vip' ? 10 : (subtotal >= 100000 ? 5 : 0);
    return Math.floor((subtotal * rate + 50) / 100);
  }

  static taxCents(order) {
    const taxable = this.subtotalCents(order) - this.discountCents(order);
    return Math.floor((taxable * 16 + 50) / 100);
  }

  static shippingCents(order) {
    return SHIPPING_CENTS[order.shippingType];
  }

  static totalCents(order) {
    return this.subtotalCents(order) - this.discountCents(order)
      + this.taxCents(order) + this.shippingCents(order);
  }

  static priority(order) {
    const subtotal = this.subtotalCents(order);
    if (subtotal >= 100000) return 'alta';
    if (subtotal >= 50000) return 'media';
    return 'baja';
  }

  static responseTimeHours(order) {
    return { alta: 2, media: 8, baja: 24 }[this.priority(order)];
  }

  static orderSummary(order) {
    return {
      id: order.id,
      subtotal: money(this.subtotalCents(order)),
      descuento: money(this.discountCents(order)),
      impuesto: money(this.taxCents(order)),
      envio: money(this.shippingCents(order)),
      total: money(this.totalCents(order)),
      prioridad: this.priority(order),
      tiempo_respuesta_horas: this.responseTimeHours(order)
    };
  }

  static updateStatus(order, status) {
    if (!ORDER_STATES.includes(status)) throw new TypeError(`Estado inválido: ${status}`);
    order.status = status;
  }

  static byCustomer(orders, name) {
    return orders.filter((order) => order.customerName.toLocaleLowerCase() === name.toLocaleLowerCase());
  }

  static byStatus(orders, status) {
    if (!ORDER_STATES.includes(status)) throw new TypeError(`Estado inválido: ${status}`);
    return orders.filter((order) => order.status === status);
  }

  static sortOrders(orders, criterion) {
    const sorted = [...orders];
    if (criterion === 'fecha') return sorted.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    if (criterion === 'monto') return sorted.sort((a, b) => this.totalCents(a) - this.totalCents(b));
    if (criterion === 'prioridad') {
      const rank = { alta: 0, media: 1, baja: 2 };
      return sorted.sort((a, b) => rank[this.priority(a)] - rank[this.priority(b)]
        || a.createdAt.localeCompare(b.createdAt));
    }
    throw new TypeError('El criterio debe ser fecha, monto o prioridad.');
  }

  static salesSummary(orders) {
    const included = orders.filter((order) => order.status !== 'Cancelado');
    const statuses = ['Recibido', 'En preparación', 'Enviado', 'Entregado', 'Cancelado'];
    const priorities = ['alta', 'media', 'baja'];
    return {
      cantidad_pedidos: orders.length,
      subtotal: money(included.reduce((sum, order) => sum + this.subtotalCents(order), 0)),
      descuentos: money(included.reduce((sum, order) => sum + this.discountCents(order), 0)),
      impuestos: money(included.reduce((sum, order) => sum + this.taxCents(order), 0)),
      envios: money(included.reduce((sum, order) => sum + this.shippingCents(order), 0)),
      ventas_total: money(included.reduce((sum, order) => sum + this.totalCents(order), 0)),
      por_estado: Object.fromEntries(statuses.map((status) => [
        status, orders.filter((order) => order.status === status).length
      ])),
      por_prioridad: Object.fromEntries(priorities.map((priority) => [
        priority, orders.filter((order) => this.priority(order) === priority).length
      ]))
    };
  }

  static async processConcurrently(orders, technicians = 4) {
    if (!Number.isInteger(technicians) || technicians < 0) {
      throw new TypeError('La cantidad de técnicos debe ser un entero no negativo.');
    }
    const priorityOrdered = this.sortOrders(orders, 'prioridad');
    if (technicians === 0) return { procesados: [], en_cola: priorityOrdered.map((order) => order.id) };

    const processed = new Array(priorityOrdered.length);
    let nextIndex = 0;
    const workers = Array.from({ length: Math.min(technicians, priorityOrdered.length) }, async () => {
      while (nextIndex < priorityOrdered.length) {
        const index = nextIndex++;
        processed[index] = this.orderSummary(priorityOrdered[index]);
        await Promise.resolve();
      }
    });
    await Promise.all(workers);
    return { procesados: processed, en_cola: [] };
  }

  static async processSequentially(orders, technicians = 4) {
    if (!Number.isInteger(technicians) || technicians < 0) {
      throw new TypeError('La cantidad de técnicos debe ser un entero no negativo.');
    }
    const ordered = this.sortOrders(orders, 'prioridad');
    if (technicians === 0) return { procesados: [], en_cola: ordered.map((order) => order.id) };
    const processed = [];
    for (const order of ordered) {
      processed.push(this.orderSummary(order));
      await Promise.resolve();
    }
    return { procesados: processed, en_cola: [] };
  }
}

module.exports = { OrderService };
