import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProdukDAO {

    // 1. CREATE / BARANG MASUK (Upsert stok + catat ke log_transaksi dalam 1 Transaction)
    public String barangMasuk(Produk p) throws SQLException {
        String sqlCek = "SELECT id FROM produk WHERE LOWER(nama_produk) = LOWER(?)";
        String sqlUpdateStok = "UPDATE produk SET stok = stok + ? WHERE id = ?";
        String sqlInsert = "INSERT INTO produk (nama_produk, stok) VALUES (?, ?)";
        String sqlLog = "INSERT INTO log_transaksi (produk_id, nama_produk, jenis, jumlah) VALUES (?, ?, 'MASUK', ?)";

        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false); // Mulai Multi-Query Transaction

            int targetId = -1;
            try (PreparedStatement psCek = conn.prepareStatement(sqlCek)) {
                psCek.setString(1, p.getNamaProduk());
                try (ResultSet rs = psCek.executeQuery()) {
                    if (rs.next()) targetId = rs.getInt("id");
                }
            }

            String pesan;
            if (targetId != -1) {
                // Tambah stok produk lama
                try (PreparedStatement psUpd = conn.prepareStatement(sqlUpdateStok)) {
                    psUpd.setInt(1, p.getStok());
                    psUpd.setInt(2, targetId);
                    psUpd.executeUpdate();
                }
                pesan = "Stok Masuk (+ " + p.getStok() + ")";
            } else {
                // Insert produk baru dan ambil ID yang baru dibuat
                try (PreparedStatement psIns = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS)) {
                    psIns.setString(1, p.getNamaProduk());
                    psIns.setInt(2, p.getStok());
                    psIns.executeUpdate();
                    try (ResultSet keys = psIns.getGeneratedKeys()) {
                        if (keys.next()) targetId = keys.getInt(1);
                    }
                }
                pesan = "Produk Baru Disimpan!";
            }

            // Catat riwayat ke tabel log_transaksi (masih dalam transaksi yang sama)
            try (PreparedStatement psLog = conn.prepareStatement(sqlLog)) {
                psLog.setInt(1, targetId);
                psLog.setString(2, p.getNamaProduk());
                psLog.setInt(3, p.getStok());
                psLog.executeUpdate();
            }

            conn.commit(); // Kedua tabel sukses -> simpan permanen
            return pesan;
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Salah satu gagal -> batalkan semua
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // 2. BARANG KELUAR (Validasi stok + kurangi stok + catat log dalam 1 Transaction)
    public void barangKeluar(int id, String namaProduk, int jumlahKeluar) throws SQLException {
        String sqlCekStok = "SELECT stok FROM produk WHERE id = ?";
        String sqlKurangStok = "UPDATE produk SET stok = stok - ? WHERE id = ?";
        String sqlLog = "INSERT INTO log_transaksi (produk_id, nama_produk, jenis, jumlah) VALUES (?, ?, 'KELUAR', ?)";

        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false); // Mulai Transaction

            // Cek apakah stok cukup
            int stokSaatIni = 0;
            try (PreparedStatement psCek = conn.prepareStatement(sqlCekStok)) {
                psCek.setInt(1, id);
                try (ResultSet rs = psCek.executeQuery()) {
                    if (rs.next()) {
                        stokSaatIni = rs.getInt("stok");
                    } else {
                        throw new SQLException("Produk tidak ditemukan!");
                    }
                }
            }

            if (jumlahKeluar > stokSaatIni) {
                throw new SQLException("Stok tidak cukup! Sisa stok saat ini: " + stokSaatIni);
            }

            // Kurangi stok
            try (PreparedStatement psKurang = conn.prepareStatement(sqlKurangStok)) {
                psKurang.setInt(1, jumlahKeluar);
                psKurang.setInt(2, id);
                psKurang.executeUpdate();
            }

            // Catat ke log_transaksi
            try (PreparedStatement psLog = conn.prepareStatement(sqlLog)) {
                psLog.setInt(1, id);
                psLog.setString(2, namaProduk);
                psLog.setInt(3, jumlahKeluar);
                psLog.executeUpdate();
            }

            conn.commit(); // Simpan permanen perubahan di kedua tabel
        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Rollback jika stok kurang atau query gagal
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // 3. READ & SEARCH (Ambil semua data atau filter berdasarkan kata kunci)
    public List<Produk> search(String keyword) throws SQLException {
        List<Produk> list = new ArrayList<>();
        String sql = "SELECT id, nama_produk, stok FROM produk WHERE nama_produk LIKE ? ORDER BY id ASC";

        try (Connection conn = KoneksiDB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Produk(
                        rs.getInt("id"),
                        rs.getString("nama_produk"),
                        rs.getInt("stok")
                    ));
                }
            }
        }
        return list;
    }

    // 4. UPDATE (Edit langsung nama & stok)
    public void update(Produk p) throws SQLException {
        String sql = "UPDATE produk SET nama_produk = ?, stok = ? WHERE id = ?";
        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, p.getNamaProduk());
                ps.setInt(2, p.getStok());
                ps.setInt(3, p.getId());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // 5. DELETE (Hapus produk)
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM produk WHERE id = ?";
        Connection conn = null;
        try {
            conn = KoneksiDB.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
}
