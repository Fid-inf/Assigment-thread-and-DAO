# Aplikasi Manajemen Stok Produk (Java Swing + JDBC + DAO + Multithreading)

**Identitas Mahasiswa:**
* **Nama:** Fido Priasa Setyono Putra
* **NIM:** F1D02410048
* **Kelas:** A

---

## Deskripsi Proyek
Aplikasi desktop berbasis **Java Swing** untuk mengelola data inventaris produk (**CRUD**: *Create, Read, Update, Delete*) yang terhubung ke database **MySQL (XAMPP)** menggunakan **JDBC**. Proyek ini dirancang dengan memisahkan lapisan akses database menggunakan pola **DAO (*Data Access Object*)**, manajemen transaksi SQL eksplisit, serta eksekusi operasi database di jalur **Multithreading** agar antarmuka pengguna tetap responsif.

---

## Penjelasan Konsep Teknis (Multithreading & DAO JDBC)

### 1. Penerapan Multithreading (`ProdukGUI.java`)
Pada Java Swing, seluruh komponen antarmuka berjalan di atas satu jalur khusus bernama **Event Dispatch Thread (EDT)**. Jika kueri database dijalankan langsung di EDT, jendela aplikasi akan membeku (*Not Responding*) selama menunggu respons server MySQL.

Untuk mengatasi hal tersebut, aplikasi ini menerapkan pola **UI $\rightarrow$ Background Thread $\rightarrow$ `invokeLater()`**:
1. **Click Handler & State Loading (EDT):** Saat pengguna menekan tombol **Save**, **Update**, atau **Delete**, method `setLoadingState(true, "Saving...")` langsung menonaktifkan seluruh tombol aksi dan mengubah label status menjadi `"Saving..."` (atau `"Updating..."` / `"Deleting..."`) untuk mencegah klik ganda.
2. **Background Worker Thread (`new Thread(() -> { ... }).start()`):** Seluruh pemanggilan method database di `ProdukDAO` dieksekusi di dalam *thread* latar belakang baru (menggunakan *Lambda Expression* dari interface `Runnable`) beserta simulasi jeda proses `Thread.sleep(600)`.
3. **Sinkronisasi Kembali ke UI (`SwingUtilities.invokeLater()`):** Setelah kueri database selesai dan data terbaru diambil, *background thread* membungkus pembaruan `JTable`, pengosongan form, serta pengaktifan kembali tombol ke dalam `SwingUtilities.invokeLater(...)` agar perubahan komponen GUI dieksekusi secara *thread-safe* di EDT.

### 2. Arsitektur DAO & Koneksi Database MySQL (`KoneksiDB.java` & `ProdukDAO.java`)
Proyek ini menerapkan pola **DAO (*Data Access Object*)** untuk memisahkan logika tampilan (`ProdukGUI`) dari logika manipulasi data SQL (`ProdukDAO`) dan representasi objek (`Produk`):
* **Koneksi JDBC (`KoneksiDB.java`):** Mengelola pembukaan jalur koneksi ke database MySQL lokal (`jdbc:mysql://localhost:3306/db_tugas_pbo`) melalui `DriverManager.getConnection()`.
* **`PreparedStatement` & `ResultSet` (`ProdukDAO.java`):** Seluruh perintah kueri (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) menggunakan parameter terikat (`?`) pada `PreparedStatement` untuk mencegah *SQL Injection*, sedangkan pembacaan data (`getAll()`) memetakan setiap baris `ResultSet` ke dalam `List<Produk>`.
* **Manajemen Transaksi (`commit` & `rollback`):** Operasi tulis pada `insertOrAddStok()`, `update()`, dan `delete()` mematikan mode simpan otomatis dengan `conn.setAutoCommit(false)`. Jika seluruh rangkaian kueri berhasil (termasuk pengecekan duplikat nama produk untuk penambahan stok otomatis / *upsert*), perubahan disahkan permanen dengan `conn.commit()`. Jika terjadi `SQLException` di tengah proses, seluruh perubahan dibatalkan menggunakan `conn.rollback()` untuk menjaga konsistensi data.

---

## Struktur Proyek
```text
tugas_crud_thread/
├── lib/
│   └── mysql-connector-j-26.10.0.jar
├── screenshots/
│   └── (berisi gambar dokumentasi aplikasi)
└── src/
    ├── Produk.java       # Model representasi entitas produk (id, namaProduk, stok)
    ├── KoneksiDB.java    # Konfigurasi koneksi JDBC ke database MySQL XAMPP
    ├── ProdukDAO.java    # Operasi CRUD, pengecekan duplikat, dan Transaction SQL
    ├── ProdukGUI.java    # Antarmuka Java Swing, JTable, dan Background Threading
    └── MainApp.java      # Entry point (main method) untuk menjalankan aplikasi
```

---

## Setup Database (MySQL XAMPP)
1. Nyalakan modul **Apache** dan **MySQL** pada XAMPP Control Panel.
2. Buka `http://localhost/phpmyadmin`, masuk ke tab **SQL**, lalu jalankan perintah berikut:
```sql
CREATE DATABASE IF NOT EXISTS db_tugas_pbo;
USE db_tugas_pbo;

CREATE TABLE produk (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nama_produk VARCHAR(100) NOT NULL,
    stok INT NOT NULL
);
```

---

## Dokumentasi & Uji Coba Fitur (Screenshots)

### 1. Tampilan Awal & Input Data Baru
Pengguna mengisi kolom **Nama Produk** (`Ultramilk full cream 1L`) dan **Stok** (`10`) saat status aplikasi `Ready`.
![Input Data Awal](screenshots/Screenshot%202026-11-26%20213213.png)

### 2. Proses Simpan Data (*Create*)
Data baru (`Ultramilk full cream 1L` dan `coca-cola 390ml`) berhasil disimpan ke MySQL melalui *background thread* dan ditampilkan ke dalam `JTable` dengan status **`Saved!`**.
![Hasil Create Data](screenshots/Screenshot%202026-11-26%20213421.png)

### 3. Persiapan Ubah Data (*Update*)
Pengguna mengklik baris `coca-cola 390ml` (ID `2`) pada tabel sehingga data otomatis terisi ke form atas, lalu mengubah nilai stok dari `20` menjadi `15`.
![Persiapan Update Data](screenshots/Screenshot%202026-11-26%20213511.png)

### 4. Hasil Ubah Data (*Update*)
Setelah tombol **Update** ditekan, data di database diperbarui, form input di-reset otomatis, dan status berubah menjadi **`Updated!`**.
![Hasil Update Data](screenshots/Screenshot%202026-11-26%20213547.png)

### 5. Hasil Hapus Data (*Delete*)
Baris data yang dipilih berhasil dihapus dari database dan tabel dengan status **`Deleted!`**.
![Hasil Delete Data](screenshots/Screenshot%202026-11-26%20213702.png)

### 6. Uji Coba Input Barang yang Sudah Ada (Sebelum Penambahan Stok)
Pengguna memilih/menginput kembali produk yang sudah terdaftar (`teh pucuk botol` dengan stok awal `5`) untuk menguji fitur penambahan stok otomatis.
![Sebelum Tambah Stok](screenshots/Screenshot%202026-11-26%20214642.png)

### 7. Hasil Penambahan Stok Otomatis (*Upsert*)
Sistem mendeteksi nama produk yang sama di dalam satu transaksi database, menjumlahkan stok lama (`5`) dengan input baru (`5`) menjadi **`10`** tanpa membuat baris duplikat, dan menampilkan status **`Stok Ditambahkan!`**.
![Hasil Tambah Stok](screenshots/Screenshot%202026-11-26%20214715.png)
