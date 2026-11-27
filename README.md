# Inventory System Pro — Java Swing + JDBC + Multi-Table DAO + Multithreading

**Identitas Mahasiswa:**
* **Nama:** Fido Priasa Setyono Putra
* **NIM:** F1D02410048
* **Kelas:** 5A — Teknik Informatika, Universitas Mataram

---

## 1. Gambaran Umum Proyek
**Inventory System Pro** adalah aplikasi desktop berbasis **Java Swing** untuk manajemen keluar-masuk stok barang (*Inventory Management*) yang terhubung secara langsung ke database relasional **MySQL (XAMPP)** menggunakan **JDBC**.

Proyek ini merupakan versi pengembangan lanjutan (*v2*) yang tidak hanya menerapkan operasi **CRUD** dasar, tetapi juga mengimplementasikan standar arsitektur perangkat lunak yang aman dari *race condition*, *SQL Injection*, dan inkonsistensi data:
* **Arsitektur DAO (*Data Access Object*):** Memisahkan sepenuhnya kode tampilan (`ProdukGUI`) dari logika kueri database (`ProdukDAO`) dan model data (`Produk`).
* **Multi-Table Database Transaction (`commit` & `rollback`):** Setiap mutasi stok (`MASUK` atau `KELUAR`) memodifikasi dua tabel sekaligus (`produk` dan `log_transaksi`) secara atomik—jika salah satu gagal atau stok tidak mencukupi, seluruh perubahan dibatalkan otomatis.
* **Asynchronous Multithreading (`Thread` + `SwingUtilities.invokeLater`):** Seluruh proses koneksi dan eksekusi kueri MySQL dijalankan di jalur latar belakang (*background worker thread*) agar antarmuka pengguna tidak pernah membeku (*freeze / Not Responding*).

---

## 2. Arsitektur & Penjelasan Tiap File (`src/`)

```text
tugas_crud_thread/
├── lib/
│   └── mysql-connector-j-26.10.0.jar   # Driver resmi JDBC MySQL Connector/J
├── screenshots/
│   └── Screenshot 2026-11-27 090243.png # Dokumentasi antarmuka aplikasi
└── src/
    ├── Produk.java                     # Model / POJO entitas produk
    ├── KoneksiDB.java                  # Manajemen koneksi JDBC ke MySQL XAMPP
    ├── ProdukDAO.java                  # Logika CRUD, Multi-Table Transaction, Search & Rollback
    ├── ProdukGUI.java                  # Antarmuka Java Swing, Renderer, & Multithreading
    └── MainApp.java                    # Entry point (main method) aplikasi
```

### Detail Tanggung Jawab Kelas
1. **`Produk.java` (Model Layer):**
   Merepresentasikan struktur tabel `produk` ke dalam objek Java dengan atribut `id`, `namaProduk`, dan `stok` beserta *getter*-nya.
2. **`KoneksiDB.java` (Database Connection Layer):**
   Menyediakan method statis `getConnection()` untuk membuka sesi koneksi ke `jdbc:mysql://localhost:3306/db_tugas_pbo` menggunakan `DriverManager`.
3. **`ProdukDAO.java` (Data Access Layer):**
   * `barangMasuk(Produk p)`: Mengecek apakah nama produk sudah ada di tabel `produk` (mengabaikan huruf besar/kecil dengan `LOWER()`). Jika sudah ada, stok lama ditambahkan (`UPDATE`); jika belum ada, baris baru dibuat (`INSERT` dengan `RETURN_GENERATED_KEYS`). Setelah itu, riwayat mutasi dicatat ke tabel `log_transaksi` dengan jenis `'MASUK'` sebelum memanggil `conn.commit()`.
   * `barangKeluar(int id, String nama, int jumlah)`: Mengecek sisa stok terkini di database. Jika `jumlah > stokSaatIni`, sistem melempar `SQLException` dan memicu `conn.rollback()`. Jika stok mencukupi, stok dikurangi di tabel `produk` dan dicatat ke `log_transaksi` dengan jenis `'KELUAR'`, lalu disahkan via `conn.commit()`.
   * `search(String keyword)`: Mengambil daftar produk menggunakan `PreparedStatement` (`WHERE nama_produk LIKE ?`) dan memetakan `ResultSet` ke dalam `List<Produk>`.
   * `update(Produk p)` & `delete(int id)`: Mengubah atau menghapus data produk berdasarkan `id` di dalam blok transaksi eksplisit.
4. **`ProdukGUI.java` (Presentation & Threading Layer):**
   * Menyediakan form input, 4 tombol aksi utama (**Stok Masuk**, **Stok Keluar**, **Update**, **Delete**), kolom pencarian (**Filter Nama**), indikator status real-time, dan `JTable`.
   * Menggunakan `DefaultTableCellRenderer` kustom untuk menyoroti baris produk dengan warna **merah muda** apabila stok kurang dari 5 unit (*low-stock alert*).
5. **`MainApp.java` (Application Entry Point):**
   Menjalankan jendela `ProdukGUI` di dalam *Event Dispatch Thread* (EDT) melalui `SwingUtilities.invokeLater()`.

---

## 3. Alur Kerja Multithreading & Transaction

Setiap kali pengguna menekan tombol aksi pada GUI, aplikasi menjalankan siklus berikut agar tetap responsif dan *thread-safe*:

```text
[User Klik Tombol] 
       │
       ▼ (Berjalan di Event Dispatch Thread / EDT)
1. Validasi input form & kunci semua tombol (setLoadingState = true)
   Label status berubah menjadi "Saving..." / "Processing..." / "Updating..."
       │
       ▼ (Membuka jalur baru: new Thread(() -> { ... }).start())
2. Background Worker Thread:
   ├── Membuka koneksi JDBC & setAutoCommit(false)
   ├── Menjalankan kueri SQL (Tabel `produk` + Tabel `log_transaksi`)
   ├── Jika sukses -> conn.commit() | Jika gagal/stok kurang -> conn.rollback()
   └── Mengambil data terbaru via dao.search()
       │
       ▼ (Kembali ke EDT via SwingUtilities.invokeLater(() -> { ... }))
3. Pembaruan UI Thread-Safe:
   ├── Mengisi ulang baris JTable dengan data terbaru
   ├── Mengosongkan form input (resetForm)
   └── Mengaktifkan tombol kembali & menampilkan status akhir ("Saved!", "Rollback!", dll.)
```

---

## 4. Panduan Instalasi & Setup Database

### Langkah 1: Konfigurasi MySQL di XAMPP
1. Buka **XAMPP Control Panel**, lalu klik **Start** pada modul **Apache** dan **MySQL**.
2. Buka `http://localhost/phpmyadmin` di browser, pilih tab **SQL**, lalu jalankan skrip berikut untuk membuat database dan dua tabel relasinya:

```sql
CREATE DATABASE IF NOT EXISTS db_tugas_pbo;
USE db_tugas_pbo;

-- Tabel utama penyimpanan data stok produk
CREATE TABLE IF NOT EXISTS produk (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nama_produk VARCHAR(100) NOT NULL,
    stok INT NOT NULL
);

-- Tabel pencatatan riwayat mutasi barang masuk & keluar
CREATE TABLE IF NOT EXISTS log_transaksi (
    id INT AUTO_INCREMENT PRIMARY KEY,
    produk_id INT NOT NULL,
    nama_produk VARCHAR(100) NOT NULL,
    jenis ENUM('MASUK', 'KELUAR') NOT NULL,
    jumlah INT NOT NULL,
    waktu TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Langkah 2: Konfigurasi Proyek di VS Code
1. Pastikan file **`mysql-connector-j-26.10.0.jar`** tersimpan di dalam folder `lib/`.
2. Buka panel **JAVA PROJECTS** di sidebar kiri bawah VS Code $\rightarrow$ klik tanda **`+`** pada bagian **Referenced Libraries** $\rightarrow$ pilih file `.jar` tersebut.
3. Buka file `src/MainApp.java`, lalu klik tombol **Run** di atas method `main(String[] args)`.

---

## 5. Panduan Uji Coba Fitur (Test Cases)

| Skenario Uji | Langkah Pengujian di Aplikasi | Hasil yang Diharapkan |
| :--- | :--- | :--- |
| **1. Tambah Produk Baru** | Isi `Nama Produk` baru dan `Jumlah / Stok`, lalu klik **Stok Masuk (Save)**. | Status berubah `"Saving..."` $\rightarrow$ `"Produk Baru Disimpan!"`, baris baru muncul di tabel, dan tercatat `MASUK` di `log_transaksi`. |
| **2. Upsert Produk Lama** | Ketik nama produk yang sudah ada di tabel dengan jumlah tambahan, lalu klik **Stok Masuk (Save)**. | Tidak membuat baris duplikat; stok lama otomatis bertambah dan tercatat `MASUK` di `log_transaksi`. |
| **3. Stok Keluar Valid** | Klik baris produk di tabel, isi jumlah keluar ($\le$ stok tersedia), lalu klik **Stok Keluar**. | Stok berkurang sesuai jumlah keluar dan tercatat `KELUAR` di `log_transaksi`. |
| **4. Uji Rollback Transaksi** | Klik produk dengan stok `10`, ketik jumlah `50`, lalu klik **Stok Keluar**. | Status berubah menjadi **`Rollback!`**, muncul dialog peringatan stok tidak cukup, dan data di kedua tabel tetap utuh. |
| **5. Peringatan Stok Tipis** | Kurangi stok suatu produk hingga tersisa kurang dari `5` unit (`1`–`4`). | Baris produk tersebut otomatis berubah warna latar belakang menjadi **merah muda** pada tabel. |
| **6. Pencarian Produk** | Ketik kata kunci (misalnya `cola`) pada kolom **Filter Nama**, lalu klik **Cari Produk**. | Tabel hanya menampilkan produk yang mengandung kata kunci tersebut. |

---

## 6. Dokumentasi Antarmuka Aplikasi

Tampilan utama **Inventory System Pro** saat terhubung dengan database MySQL XAMPP, menampilkan form mutasi stok, tombol aksi CRUD, filter pencarian real-time, status *thread* (`Ready`), serta daftar produk yang tersimpan:

![Tampilan Inventory System Pro](screenshots/Screenshot%202026-11-27%20090243.png)
````[cite: 10]
