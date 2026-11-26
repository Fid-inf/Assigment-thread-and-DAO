import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProdukDAO {

    // 1. CREATE / UPSERT (Cek duplikat nama barang + Transaction)
    public String insertOrAddStok(Produk p) throws SQLException {
        String sqlCek = "SELECT id, stok FROM produk WHERE LOWER(nama_produk) = LOWER(?)";
        String sqlUpdateStok = "UPDATE produk SET stok = stok + ? WHERE id = ?";
        String sqlInsert = "INSERT INTO produk (nama_produk, stok) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false); // Memulai Transaction (perubahan belum permanen)

            // Tahap A: Cek apakah nama produk sudah ada di database
            int existingId = -1;
            try (PreparedStatement psCek = conn.prepareStatement(sqlCek)) {
                psCek.setString(1, p.getNamaProduk());
                try (ResultSet rs = psCek.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id"); // Ambil ID jika barang sudah ada
                    }
                }
            }

            // Tahap B: Tentukan apakah UPDATE stok lama atau INSERT baris baru
            String hasil;
            if (existingId != -1) {
                // Jika barang sudah ada -> tambahkan jumlah stoknya
                try (PreparedStatement psUpd = conn.prepareStatement(sqlUpdateStok)) {
                    psUpd.setInt(1, p.getStok());
                    psUpd.setInt(2, existingId);
                    psUpd.executeUpdate();
                }
                hasil = "Stok Ditambahkan!";
            } else {
                // Jika barang belum ada -> simpan sebagai data baru
                try (PreparedStatement psIns = conn.prepareStatement(sqlInsert)) {
                    psIns.setString(1, p.getNamaProduk());
                    psIns.setInt(2, p.getStok());
                    psIns.executeUpdate();
                }
                hasil = "Saved (Baru)!";
            }

            conn.commit(); // Simpan permanen seluruh rangkaian transaksi
            return hasil;
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Batalkan semua perubahan jika terjadi error
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true); // Kembalikan mode koneksi ke normal
                conn.close();
            }
        }
    }

    // 2. READ (Mengambil semua data produk menggunakan ResultSet)
    public List<Produk> getAll() throws SQLException {
        List<Produk> list = new ArrayList<>();
        String sql = "SELECT id, nama_produk, stok FROM produk ORDER BY id ASC";

        try (Connection conn = KoneksiDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            // Membaca hasil query baris demi baris dari database
            while (rs.next()) {
                list.add(new Produk(
                    rs.getInt("id"),
                    rs.getString("nama_produk"),
                    rs.getInt("stok")
                ));
            }
        }
        return list;
    }

    // 3. UPDATE (Mengubah nama & stok berdasarkan ID dengan Transaction)
    public void update(Produk p) throws SQLException {
        String sql = "UPDATE produk SET nama_produk = ?, stok = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false); // Mulai Transaction

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getNamaProduk());
                ps.setInt(2, p.getStok());
                ps.setInt(3, p.getId());
                ps.executeUpdate();
            }

            conn.commit(); // Simpan permanen
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Batalkan jika gagal
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // 4. DELETE (Menghapus data produk berdasarkan ID dengan Transaction)
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM produk WHERE id = ?";
        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false); // Mulai Transaction

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            conn.commit(); // Simpan permanen
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Batalkan jika gagal
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
}