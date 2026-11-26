import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ProdukGUI extends JFrame {
    private JTextField tfId, tfNama, tfStok;
    private JButton btnSave, btnUpdate, btnDelete;
    private JLabel lblStatus;
    private JTable table;
    private DefaultTableModel tableModel;
    private final ProdukDAO dao = new ProdukDAO();

    public ProdukGUI() {
        super("CRUD Produk - DAO + Transaction + Thread");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- BAGIAN 1: PANEL FORM INPUT ---
        JPanel panelForm = new JPanel(new GridLayout(3, 2, 8, 8));
        tfId = new JTextField();
        tfId.setEditable(false); // Dikunci agar user tidak mengubah ID secara manual
        tfId.setBackground(new Color(235, 235, 235));
        tfNama = new JTextField();
        tfStok = new JTextField();

        panelForm.add(new JLabel("ID (Otomatis saat klik tabel):"));
        panelForm.add(tfId);
        panelForm.add(new JLabel("Nama Produk:"));
        panelForm.add(tfNama);
        panelForm.add(new JLabel("Stok:"));
        panelForm.add(tfStok);

        // --- BAGIAN 2: PANEL TOMBOL & LABEL STATUS ("Saving...") ---
        btnSave = new JButton("Save (Create)");
        btnUpdate = new JButton("Update");
        btnDelete = new JButton("Delete");

        lblStatus = new JLabel("Ready");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(new Color(0, 128, 0));

        JPanel panelAksi = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        panelAksi.add(btnSave);
        panelAksi.add(btnUpdate);
        panelAksi.add(btnDelete);
        panelAksi.add(Box.createHorizontalStrut(15));
        panelAksi.add(new JLabel("Status: "));
        panelAksi.add(lblStatus);

        JPanel panelAtas = new JPanel(new BorderLayout(5, 5));
        panelAtas.add(panelForm, BorderLayout.CENTER);
        panelAtas.add(panelAksi, BorderLayout.SOUTH);

        // --- BAGIAN 3: TABEL DATA (3 Kolom Utama) ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Nama Produk", "Stok"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        JScrollPane scrollTable = new JScrollPane(table);

        // Event klik baris tabel -> salin data ke form untuk proses Update / Delete
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                tfId.setText(tableModel.getValueAt(row, 0).toString());   // Kolom 0: ID
                tfNama.setText(tableModel.getValueAt(row, 1).toString()); // Kolom 1: Nama Produk
                tfStok.setText(tableModel.getValueAt(row, 2).toString()); // Kolom 2: Stok
            }
        });

        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(panelAtas, BorderLayout.NORTH);
        add(scrollTable, BorderLayout.CENTER);

        // --- BAGIAN 4: DAFTARKAN CLICK HANDLER TOMBOL CRUD ---
        btnSave.addActionListener(e -> prosesCreate());
        btnUpdate.addActionListener(e -> prosesUpdate());
        btnDelete.addActionListener(e -> prosesDelete());

        setSize(560, 420);
        setLocationRelativeTo(null);

        // Muat data awal dari database saat aplikasi pertama dibuka
        loadDataTabel();
    }

    // Helper untuk mengunci/membuka tombol dan mengganti teks status
    private void setLoadingState(boolean loading, String pesanStatus) {
        btnSave.setEnabled(!loading);
        btnUpdate.setEnabled(!loading);
        btnDelete.setEnabled(!loading);
        lblStatus.setText(pesanStatus);
        lblStatus.setForeground(loading ? Color.BLUE : new Color(0, 128, 0));
    }

    // Helper untuk mengosongkan form setelah simpan/ubah/hapus berhasil
    private void resetForm() {
        tfId.setText("");
        tfNama.setText("");
        tfStok.setText("");
        table.clearSelection();
    }

    // --- OPERASI CREATE (Click Handler -> Disable Button -> "Saving..." -> Thread -> invokeLater) ---
    private void prosesCreate() {
        String nama = tfNama.getText().trim();
        String stokStr = tfStok.getText().trim();
        if (nama.isEmpty() || stokStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nama produk dan stok wajib diisi!");
            return;
        }

        int stok;
        try {
            stok = Integer.parseInt(stokStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Stok harus berupa angka!");
            return;
        }

        // Langkah 1: Disable tombol & tampilkan label "Saving..." di GUI Thread
        setLoadingState(true, "Saving...");

        // Langkah 2: Jalankan query database di background thread agar GUI tidak freeze
        new Thread(() -> {
            try {
                Thread.sleep(600); // Simulasi jeda jaringan agar status "Saving..." terlihat
                String statusSimpan = dao.insertOrAddStok(new Produk(0, nama, stok));
                List<Produk> dataTerbaru = dao.getAll();

                // Langkah 3: Kembali ke Event Dispatch Thread (EDT) untuk update tabel & aktifkan tombol
                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(dataTerbaru);
                    resetForm();
                    setLoadingState(false, statusSimpan);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, "Gagal menyimpan: " + ex.getMessage());
                });
            }
        }).start();
    }

    // --- OPERASI UPDATE ---
    private void prosesUpdate() {
        if (tfId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pilih baris di tabel terlebih dahulu!");
            return;
        }

        int id = Integer.parseInt(tfId.getText());
        String nama = tfNama.getText().trim();
        int stok;
        try {
            stok = Integer.parseInt(tfStok.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Stok harus berupa angka!");
            return;
        }

        // Langkah 1: Disable tombol & tampilkan "Updating..."
        setLoadingState(true, "Updating...");

        // Langkah 2: Eksekusi update DB di background thread
        new Thread(() -> {
            try {
                Thread.sleep(600);
                dao.update(new Produk(id, nama, stok));
                List<Produk> dataTerbaru = dao.getAll();

                // Langkah 3: Update UI lewat invokeLater
                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(dataTerbaru);
                    resetForm();
                    setLoadingState(false, "Updated!");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, "Gagal update: " + ex.getMessage());
                });
            }
        }).start();
    }

    // --- OPERASI DELETE ---
    private void prosesDelete() {
        if (tfId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pilih baris di tabel terlebih dahulu!");
            return;
        }

        int id = Integer.parseInt(tfId.getText());

        // Langkah 1: Disable tombol & tampilkan "Deleting..."
        setLoadingState(true, "Deleting...");

        // Langkah 2: Eksekusi delete DB di background thread
        new Thread(() -> {
            try {
                Thread.sleep(600);
                dao.delete(id);
                List<Produk> dataTerbaru = dao.getAll();

                // Langkah 3: Update UI lewat invokeLater
                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(dataTerbaru);
                    resetForm();
                    setLoadingState(false, "Deleted!");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, "Gagal menghapus: " + ex.getMessage());
                });
            }
        }).start();
    }

    // --- OPERASI READ AWAL SAAT APLIKASI DIBUKA ---
    private void loadDataTabel() {
        setLoadingState(true, "Loading...");
        new Thread(() -> {
            try {
                List<Produk> data = dao.getAll();
                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    setLoadingState(false, "Ready");
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "DB Error");
                    JOptionPane.showMessageDialog(this, "Gagal memuat data: " + ex.getMessage());
                });
            }
        }).start();
    }

    // Mengisi ulang baris JTable dengan data terbaru dari database
    private void perbaruiTabel(List<Produk> list) {
        tableModel.setRowCount(0);
        for (Produk p : list) {
            tableModel.addRow(new Object[]{p.getId(), p.getNamaProduk(), p.getStok()});
        }
    }
}