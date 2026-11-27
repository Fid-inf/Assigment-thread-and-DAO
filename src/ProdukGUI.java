import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class ProdukGUI extends JFrame {
    private JTextField tfId, tfNama, tfStok, tfCari;
    private JButton btnSave, btnKeluar, btnUpdate, btnDelete, btnCari;
    private JLabel lblStatus;
    private JTable table;
    private DefaultTableModel tableModel;
    private final ProdukDAO dao = new ProdukDAO();

    public ProdukGUI() {
        super("Inventory System Pro - Multi-Table Transaction & Thread");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // --- PANEL FORM INPUT ---
        JPanel panelForm = new JPanel(new GridLayout(3, 2, 8, 8));
        tfId = new JTextField();
        tfId.setEditable(false);
        tfId.setBackground(new Color(235, 235, 235));
        tfNama = new JTextField();
        tfStok = new JTextField();

        panelForm.add(new JLabel("ID (Klik tabel untuk pilih):"));
        panelForm.add(tfId);
        panelForm.add(new JLabel("Nama Produk:"));
        panelForm.add(tfNama);
        panelForm.add(new JLabel("Jumlah / Stok:"));
        panelForm.add(tfStok);

        // --- PANEL TOMBOL AKSI & STATUS ---
        btnSave = new JButton("Stok Masuk (Save)");
        btnKeluar = new JButton("Stok Keluar");
        btnUpdate = new JButton("Update");
        btnDelete = new JButton("Delete");

        lblStatus = new JLabel("Ready");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(new Color(0, 128, 0));

        JPanel panelTombol = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        panelTombol.add(btnSave);
        panelTombol.add(btnKeluar);
        panelTombol.add(btnUpdate);
        panelTombol.add(btnDelete);
        panelTombol.add(Box.createHorizontalStrut(10));
        panelTombol.add(new JLabel("Status: "));
        panelTombol.add(lblStatus);

        // --- PANEL PENCARIAN ---
        JPanel panelCari = new JPanel(new BorderLayout(6, 0));
        tfCari = new JTextField();
        btnCari = new JButton("Cari Produk");
        panelCari.add(new JLabel("Filter Nama: "), BorderLayout.WEST);
        panelCari.add(tfCari, BorderLayout.CENTER);
        panelCari.add(btnCari, BorderLayout.EAST);

        JPanel panelAtas = new JPanel(new BorderLayout(5, 8));
        panelAtas.add(panelForm, BorderLayout.NORTH);
        panelAtas.add(panelTombol, BorderLayout.CENTER);
        panelAtas.add(panelCari, BorderLayout.SOUTH);

        // --- TABEL DATA DENGAN HIGHLIGHT STOK MENIPIS (< 5) ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Nama Produk", "Stok"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);

        // Warnai baris menjadi merah muda jika stok < 5
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, val, isSelected, hasFocus, row, col);
                int stok = Integer.parseInt(tbl.getValueAt(row, 2).toString());
                if (!isSelected) {
                    c.setBackground(stok < 5 ? new Color(255, 225, 225) : Color.WHITE);
                }
                return c;
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                tfId.setText(tableModel.getValueAt(row, 0).toString());
                tfNama.setText(tableModel.getValueAt(row, 1).toString());
                tfStok.setText(tableModel.getValueAt(row, 2).toString());
            }
        });

        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(panelAtas, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Event Handler
        btnSave.addActionListener(e -> prosesBarangMasuk());
        btnKeluar.addActionListener(e -> prosesBarangKeluar());
        btnUpdate.addActionListener(e -> prosesUpdate());
        btnDelete.addActionListener(e -> prosesDelete());
        btnCari.addActionListener(e -> loadDataTabel(tfCari.getText().trim()));

        setSize(620, 460);
        setLocationRelativeTo(null);
        loadDataTabel("");
    }

    private void setLoadingState(boolean loading, String pesanStatus) {
        btnSave.setEnabled(!loading);
        btnKeluar.setEnabled(!loading);
        btnUpdate.setEnabled(!loading);
        btnDelete.setEnabled(!loading);
        btnCari.setEnabled(!loading);
        lblStatus.setText(pesanStatus);
        lblStatus.setForeground(loading ? Color.BLUE : new Color(0, 128, 0));
    }

    private void resetForm() {
        tfId.setText("");
        tfNama.setText("");
        tfStok.setText("");
        table.clearSelection();
    }

    private void prosesBarangMasuk() {
        String nama = tfNama.getText().trim();
        int jumlah;
        try {
            jumlah = Integer.parseInt(tfStok.getText().trim());
            if (nama.isEmpty() || jumlah <= 0) throw new IllegalArgumentException();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Masukkan nama produk dan jumlah angka > 0!");
            return;
        }

        setLoadingState(true, "Saving...");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                String status = dao.barangMasuk(new Produk(0, nama, jumlah));
                List<Produk> data = dao.search(tfCari.getText().trim());

                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    resetForm();
                    setLoadingState(false, status);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                });
            }
        }).start();
    }

    private void prosesBarangKeluar() {
        if (tfId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pilih produk di tabel terlebih dahulu!");
            return;
        }
        int id = Integer.parseInt(tfId.getText());
        String nama = tfNama.getText().trim();
        int jumlahKeluar;
        try {
            jumlahKeluar = Integer.parseInt(tfStok.getText().trim());
            if (jumlahKeluar <= 0) throw new IllegalArgumentException();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Masukkan jumlah barang keluar (> 0)!");
            return;
        }

        setLoadingState(true, "Processing...");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                dao.barangKeluar(id, nama, jumlahKeluar);
                List<Produk> data = dao.search(tfCari.getText().trim());

                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    resetForm();
                    setLoadingState(false, "Stok Keluar (-" + jumlahKeluar + ")");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Rollback!");
                    JOptionPane.showMessageDialog(this, "Transaksi Dibatalkan: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void prosesUpdate() {
        if (tfId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pilih baris di tabel terlebih dahulu!");
            return;
        }
        int id = Integer.parseInt(tfId.getText());
        String nama = tfNama.getText().trim();
        int stok = Integer.parseInt(tfStok.getText().trim());

        setLoadingState(true, "Updating...");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                dao.update(new Produk(id, nama, stok));
                List<Produk> data = dao.search(tfCari.getText().trim());

                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    resetForm();
                    setLoadingState(false, "Updated!");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                });
            }
        }).start();
    }

    private void prosesDelete() {
        if (tfId.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Pilih baris di tabel terlebih dahulu!");
            return;
        }
        int id = Integer.parseInt(tfId.getText());

        setLoadingState(true, "Deleting...");
        new Thread(() -> {
            try {
                Thread.sleep(500);
                dao.delete(id);
                List<Produk> data = dao.search(tfCari.getText().trim());

                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    resetForm();
                    setLoadingState(false, "Deleted!");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "Error!");
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                });
            }
        }).start();
    }

    private void loadDataTabel(String keyword) {
        setLoadingState(true, "Loading...");
        new Thread(() -> {
            try {
                List<Produk> data = dao.search(keyword);
                SwingUtilities.invokeLater(() -> {
                    perbaruiTabel(data);
                    setLoadingState(false, "Ready");
                });
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> {
                    setLoadingState(false, "DB Error");
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                });
            }
        }).start();
    }

    private void perbaruiTabel(List<Produk> list) {
        tableModel.setRowCount(0);
        for (Produk p : list) {
            tableModel.addRow(new Object[]{p.getId(), p.getNamaProduk(), p.getStok()});
        }
    }
}
