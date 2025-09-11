// File: Main.java
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Uso: java Main sim <totalMarcos> <nproc>");
            System.out.println("Ejemplo: java Main sim 8 2");
            return;
        }
        if (!args[0].equalsIgnoreCase("sim")) {
            System.out.println("Modo no soportado. Use: sim");
            return;
        }
        int totalMarcos = Integer.parseInt(args[1]);
        int nproc = Integer.parseInt(args[2]);
        Simulator sim = new Simulator(totalMarcos, nproc);
        sim.run();
    }
}
