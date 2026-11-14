const ORDER_STATES = ['Recibido', 'En preparación', 'Enviado', 'Entregado', 'Cancelado'];
const SHIPPING_TYPES = ['estandar', 'express', 'retiro'];
const PAYMENT_METHODS = ['tarjeta_simulada', 'efectivo_simulado', 'transferencia_simulada'];
const CUSTOMER_TYPES = ['regular', 'vip'];

class Product {
  constructor({ codigo, nombre, precio, cantidad }) {
    if (!String(codigo || '').trim() || !String(nombre || '').trim()) {
      throw new TypeError('El producto requiere código y nombre.');
    }
    if (typeof precio !== 'number' || !Number.isFinite(precio) || precio < 0) {
      throw new TypeError('El precio debe ser un número no negativo.');
    }
    if (!Number.isInteger(cantidad) || cantidad <= 0) {
      throw new TypeError('La cantidad solicitada debe ser mayor que cero.');
    }
    this.code = String(codigo).trim();
    this.name = String(nombre).trim();
    this.price = precio;
    this.quantity = cantidad;
  }

  toJSON() {
    return { codigo: this.code, nombre: this.name, precio: this.price, cantidad: this.quantity };
  }
}

class Order {
  constructor(data) {
    const customer = data.cliente;
    if (!String(data.id || '').trim() || !customer || !String(customer.nombre || '').trim()) {
      throw new TypeError('El pedido requiere un identificador y nombre de cliente.');
    }
    if (!CUSTOMER_TYPES.includes(customer.tipo)) throw new TypeError(`Tipo de cliente inválido: ${customer.tipo}`);
    if (!SHIPPING_TYPES.includes(data.tipo_envio)) throw new TypeError(`Tipo de envío inválido: ${data.tipo_envio}`);
    if (!PAYMENT_METHODS.includes(data.metodo_pago)) throw new TypeError(`Método de pago inválido: ${data.metodo_pago}`);
    if (!ORDER_STATES.includes(data.estado)) throw new TypeError(`Estado inválido: ${data.estado}`);
    if (!Number.isFinite(Date.parse(data.fecha_creacion))) throw new TypeError('La fecha debe usar formato ISO 8601.');
    if (!Array.isArray(data.productos) || data.productos.length === 0) {
      throw new TypeError('Un pedido debe contener al menos un producto.');
    }

    this.id = String(data.id);
    this.customerName = String(customer.nombre);
    this.customerType = customer.tipo;
    this.products = data.productos.map((product) => new Product(product));
    this.shippingType = data.tipo_envio;
    this.paymentMethod = data.metodo_pago;
    this.status = data.estado;
    this.createdAt = data.fecha_creacion;
  }

  addProduct(product) {
    this.addProducts([product]);
  }

  addProducts(products) {
    if (!Array.isArray(products) || products.length === 0) {
      throw new TypeError('Se requiere al menos un producto.');
    }
    const validated = products.map((product) => (product instanceof Product ? product : new Product(product)));
    this.products.push(...validated);
  }

  toJSON() {
    return {
      id: this.id,
      cliente: { nombre: this.customerName, tipo: this.customerType },
      productos: this.products.map((product) => product.toJSON()),
      tipo_envio: this.shippingType,
      metodo_pago: this.paymentMethod,
      estado: this.status,
      fecha_creacion: this.createdAt
    };
  }
}

module.exports = { CUSTOMER_TYPES, ORDER_STATES, Order, Product };
