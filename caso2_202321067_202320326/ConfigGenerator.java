// Archivo: ConfigGenerator.java

public class ConfigGenerator {
    // Lee archivo de configuración con TP, NPROC, TAMS y genera los proc<i>.txt
    public static void gen(String cfgFile) throws Exception {
        // (1) parsear archivo cfg (líneas o key=value)
        // (2) para cada tamaño en TAMS (p.ej. 4x4) calcular NF, NC
        // (3) simular el recorrido row-major para generar direcciones dv usando TP y 4 bytes/int
        // (4) escribir proc<i>.txt con cabecera TP,NF,NC,NR,NP y lista de referencias
    }
}
