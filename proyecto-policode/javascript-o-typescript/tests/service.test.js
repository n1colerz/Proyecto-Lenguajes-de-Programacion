const { Order, Product } = require('../src/model');
const { OrderService } = require('../src/service');

function sampleOrder(overrides = {}) {
  const fields = {
    id: 'T-1',
    cliente: { nombre: 'Test', tipo: 'regular' },
    productos: [{ codigo: 'A', nombre: 'Artículo', precio: 100, cantidad: 2 }],
    tipo_envio: 'estandar',
    metodo_pago: 'tarjeta_simulada',
    estado: 'Recibido',
    fecha_creacion: '2026-01-01T10:00:00',
    ...overrides
  };
  return new Order(fields);
}

describe('OrderService', () => {
  test('calcula subtotal, descuento VIP e impuesto', () => {
    const order = sampleOrder({ cliente: { nombre: 'Test', tipo: 'vip' } });
    expect(OrderService.subtotalCents(order)).toBe(20000);
    expect(OrderService.discountCents(order)).toBe(2000);
    expect(OrderService.taxCents(order)).toBe(2880);
  });

  test('aplica descuento por monto y calcula envío', () => {
    const threshold = sampleOrder({ productos: [{ codigo: 'A', nombre: 'A', precio: 1000, cantidad: 1 }] });
    expect(OrderService.discountCents(threshold)).toBe(5000);
    expect(OrderService.shippingCents(sampleOrder({ tipo_envio: 'express' }))).toBe(10000);
    expect(OrderService.shippingCents(sampleOrder({ tipo_envio: 'retiro' }))).toBe(0);
  });

  test('rechaza cantidades no positivas y pedidos vacíos', () => {
    expect(() => new Product({ codigo: 'A', nombre: 'Artículo', precio: 1, cantidad: 0 })).toThrow();
    expect(() => sampleOrder({ productos: [] })).toThrow('al menos un producto');
  });

  test('cambia y filtra estado; consulta cliente', () => {
    const order = sampleOrder();
    OrderService.updateStatus(order, 'Enviado');
    expect(OrderService.byStatus([order], 'Enviado')).toEqual([order]);
    expect(OrderService.byCustomer([order], 'test')).toEqual([order]);
    expect(() => OrderService.updateStatus(order, 'Desconocido')).toThrow();
  });

  test('ordena por fecha, monto y prioridad', () => {
    const old = sampleOrder({ id: 'old' });
    const high = sampleOrder({
      id: 'high',
      productos: [{ codigo: 'A', nombre: 'A', precio: 1000, cantidad: 1 }],
      fecha_creacion: '2026-01-02T10:00:00'
    });
    expect(OrderService.sortOrders([high, old], 'fecha')[0].id).toBe('old');
    expect(OrderService.sortOrders([high, old], 'monto')[0].id).toBe('old');
    expect(OrderService.sortOrders([old, high], 'prioridad')[0].id).toBe('high');
  });

  test('asigna plazo y deja pedidos en cola sin técnicos', async () => {
    const critical = sampleOrder({
      productos: [{ codigo: 'A', nombre: 'A', precio: 1000, cantidad: 1 }]
    });
    expect(OrderService.responseTimeHours(critical)).toBe(2);
    await expect(OrderService.processConcurrently([critical], 0))
      .resolves.toEqual({ procesados: [], en_cola: ['T-1'] });
    await expect(OrderService.processSequentially([critical]))
      .resolves.toEqual(await OrderService.processConcurrently([critical]));
  });

  test('permite agregar productos a un pedido', () => {
    const order = sampleOrder();
    order.addProducts([
      new Product({ codigo: 'B', nombre: 'Otro', precio: 50, cantidad: 1 }),
      new Product({ codigo: 'C', nombre: 'Tercero', precio: 75, cantidad: 2 })
    ]);
    expect(order.products).toHaveLength(3);
  });
});
