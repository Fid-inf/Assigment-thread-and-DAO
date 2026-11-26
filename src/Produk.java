public class Produk {
    private int id;
    private String namaProduk;
    private int stok;

    public Produk(int id, String namaProduk, int stok) {
        this.id = id;
        this.namaProduk = namaProduk;
        this.stok = stok;
    }

    public int getId() { return id; }
    public String getNamaProduk() { return namaProduk; }
    public int getStok() { return stok; }
}