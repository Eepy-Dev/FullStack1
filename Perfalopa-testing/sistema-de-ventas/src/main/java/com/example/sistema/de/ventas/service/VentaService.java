package com.example.sistema.de.ventas.service;

import com.example.sistema.de.ventas.dto.DevolucionRequestDTO;
import com.example.sistema.de.ventas.dto.VentaRequestDTO;
import com.example.sistema.de.ventas.model.DetalleVenta;
import com.example.sistema.de.ventas.model.Devolucion;
import com.example.sistema.de.ventas.model.Factura;
import com.example.sistema.de.ventas.model.Venta;
import com.example.sistema.de.ventas.repository.DetalleVentaRepository;
import com.example.sistema.de.ventas.repository.DevolucionRepository;
import com.example.sistema.de.ventas.repository.FacturaRepository;
import com.example.sistema.de.ventas.repository.VentaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final FacturaRepository facturaRepository;
    private final DevolucionRepository devolucionRepository;
    private final RestTemplate restTemplate;

    // Inyección por constructor
    public VentaService(VentaRepository ventaRepository,
                        DetalleVentaRepository detalleVentaRepository,
                        FacturaRepository facturaRepository,
                        DevolucionRepository devolucionRepository,
                        RestTemplate restTemplate) {
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.facturaRepository = facturaRepository;
        this.devolucionRepository = devolucionRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public Venta registrarVenta(VentaRequestDTO ventaRequest) {
        // Crear la venta
        Venta venta = new Venta();
        venta.setClienteId(ventaRequest.getClienteId());
        venta.setFecha(LocalDateTime.now());
        venta.setTipoVenta(ventaRequest.getTipoVenta());

        List<DetalleVenta> detalles = ventaRequest.getDetalles().stream().map(dto -> {
            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProductoId(dto.getProductoId());
            detalle.setCantidad(dto.getCantidad());

            Double precioUnitario = obtenerPrecioProducto(dto.getProductoId());
            detalle.setPrecioUnitario(precioUnitario);
            detalle.setSubtotal(precioUnitario * dto.getCantidad());
            return detalle;
        }).collect(Collectors.toList());

        venta.setDetalles(detalles);

        Double total = detalles.stream()
                .mapToDouble(DetalleVenta::getSubtotal)
                .sum();

        if (ventaRequest.getCodigoDescuento() != null) {
            total = aplicarDescuento(total, ventaRequest.getCodigoDescuento());
        }

        venta.setTotal(total);

        Venta ventaGuardada = ventaRepository.save(venta); 

        Factura factura = new Factura();
        factura.setVenta(ventaGuardada);
        factura.setFechaEmision(LocalDateTime.now());
        factura.setTotal(total);
        facturaRepository.save(factura);

        for (DetalleVenta detalle : detalles) {
            actualizarInventario(detalle.getProductoId(), detalle.getCantidad());
        }

        registrarCompraCliente(ventaGuardada.getClienteId(), ventaGuardada.getId());

        return ventaGuardada;
    }

    @Transactional
    public Devolucion procesarDevolucion(DevolucionRequestDTO devolucionRequest) {
        // Buscar la venta
        Venta venta = ventaRepository.findById(devolucionRequest.getVentaId())
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        DetalleVenta detalle = venta.getDetalles().stream()
                .filter(d -> d.getProductoId().equals(devolucionRequest.getProductoId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado en la venta"));

        if (devolucionRequest.getCantidad() > detalle.getCantidad()) {
            throw new IllegalArgumentException("Cantidad a devolver excede la cantidad comprada");
        }

        Devolucion devolucion = new Devolucion();
        devolucion.setVenta(venta);
        devolucion.setProductoId(devolucionRequest.getProductoId());
        devolucion.setCantidad(devolucionRequest.getCantidad());
        devolucion.setMotivo(devolucionRequest.getMotivo());
        devolucion.setFechaDevolucion(LocalDateTime.now());

       
        Double montoReembolsado = (detalle.getSubtotal() / detalle.getCantidad()) * devolucionRequest.getCantidad();
        devolucion.setMontoReembolsado(montoReembolsado);

        Devolucion devolucionGuardada = devolucionRepository.save(devolucion);

        actualizarInventarioDevolucion(devolucionGuardada.getProductoId(), devolucionGuardada.getCantidad());

        registrarDevolucionCliente(venta.getClienteId(), devolucionGuardada.getId());

        return devolucionGuardada;
    }

    private Double obtenerPrecioProducto(Long productoId) {
        
        String url = "http://localhost:8082/api/inventario/productos/" + productoId;
        try {
            return restTemplate.getForObject(url, Double.class);
        } catch (Exception e) {
            // Simulamos un precio por ahora
            return 100.0;
        }
    }

    private Double aplicarDescuento(Double total, String codigoDescuento) {
        
        if (codigoDescuento.equals("DESC10")) {
            return total * 0.9; // 10% de descuento
        }
        return total; 
    }

    private void actualizarInventario(Long productoId, Integer cantidad) {
        // Simulamos una llamada al Servicio de Inventario para reducir el stock
        String url = "http://localhost:8082/api/inventario/actualizar-stock/" + productoId + "?cantidad=" + (-cantidad);
        try {
            restTemplate.postForObject(url, null, Void.class);
        } catch (Exception e) {
           
        }
    }

    private void registrarCompraCliente(Long clienteId, Long ventaId) {
        
        String url = "http://localhost:8083/api/clientes/" + clienteId + "/compras";
        try {
            restTemplate.postForObject(url, ventaId, Void.class);
        } catch (Exception e) {
           
        }
    }

    private void actualizarInventarioDevolucion(Long productoId, Integer cantidad) {
    
        String url = "http://localhost:8082/api/inventario/actualizar-stock/" + productoId + "?cantidad=" + cantidad;
        try {
            restTemplate.postForObject(url, null, Void.class);
        } catch (Exception e) {
          
        }
    }

    private void registrarDevolucionCliente(Long clienteId, Long devolucionId) {
    
        String url = "http://localhost:8083/api/clientes/" + clienteId + "/devoluciones";
        try {
            restTemplate.postForObject(url, devolucionId, Void.class);
        } catch (Exception e) {
        
        }
    }
}
