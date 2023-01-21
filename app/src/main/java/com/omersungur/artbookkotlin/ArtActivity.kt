package com.omersungur.artbookkotlin

import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.omersungur.artbookkotlin.databinding.ActivityArtBinding
import java.io.ByteArrayOutputStream

class ArtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent> // Aktiviteyi bir sonuç için başlatıyoruz.
    private lateinit var permissionLauncher: ActivityResultLauncher<String> // İzinler string gelir.
    private lateinit var selectedBitmap: Bitmap
    private lateinit var database : SQLiteDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArtBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        database = this.openOrCreateDatabase("Arts", MODE_PRIVATE, null)

        registerLauncher()

        val intent = intent

        val info = intent.getStringExtra("Info")
        if(info.equals("New")) {
            binding.textName.setText("")
            binding.textArtistName.setText("")
            binding.textYear.setText("")
            binding.button.isVisible = true
            binding.imageView.setImageResource(R.drawable.selectitem)
        }
        else {
            binding.button.isVisible = false

            val selectedId = intent.getIntExtra("id",1)
            val cursor = database.rawQuery("SELECT * FROM arts WHERE id = ?", arrayOf(selectedId.toString()))

            val artNameIx = cursor.getColumnIndex("artName")
            val artistNameIx = cursor.getColumnIndex("artistName")
            val artYearIx = cursor.getColumnIndex("artYear")
            val imageIx = cursor.getColumnIndex("artImage")

            while(cursor.moveToNext()) {
                binding.textName.setText(cursor.getString(artNameIx))
                binding.textArtistName.setText(cursor.getString(artistNameIx))
                binding.textYear.setText(cursor.getString(artYearIx))

                val byteArray = cursor.getBlob(imageIx) // veri tabanına resmi kaydetmek için ByteArray yapmalıyız.
                val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
                binding.imageView.setImageBitmap(bitmap)
            }
            cursor.close()
        }
    }

    private fun makeSmallerBitmap(image: Bitmap, maximumSize: Int): Bitmap {
        var width = image.width
        var height = image.height

        var bitmapRatio: Double = width.toDouble() / height.toDouble()

        if (bitmapRatio > 1) { // bitmapRatio 1'den büyükse ekran yataydır, değilse dikeydir. Eşitse ekran karedir.
            width = maximumSize
            val scaledHeight = width / bitmapRatio
            height = scaledHeight.toInt()
        } else {
            height = maximumSize
            val scaledWidth = height * bitmapRatio
            width = scaledWidth.toInt()
        }

        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    fun saveArt(view: View) {
        val artName = binding.textName.text.toString()
        val artistName = binding.textArtistName.text.toString()
        val yearText = binding.textYear.text.toString()

        if (selectedBitmap != null) {
            val smallBitmap = makeSmallerBitmap(selectedBitmap!!, 300)

            val outputStream =
                ByteArrayOutputStream() // Resmi veri tabanına kaydetmek için byteArray'e çevirmeliyiz.
            smallBitmap.compress(Bitmap.CompressFormat.PNG, 50, outputStream)
            val byteArray = outputStream.toByteArray()

            try {

                database.execSQL("CREATE TABLE IF NOT EXISTS arts (id INTEGER PRIMARY KEY, artName VARCHAR, artistName VARCHAR, artYear VARCHAR, artImage BLOB)")

                val sqlString =
                    "INSERT INTO arts (artName, artistName, artYear, artImage) VALUES (?, ?, ?, ?)"
                val statement = database.compileStatement(sqlString) // database'e bağlama işlemini yapmadan kayıt yapmıyoruz.
                statement.bindString(1, artName)
                statement.bindString(2, artistName)
                statement.bindString(3, yearText)
                statement.bindBlob(4, byteArray)
                statement.execute() // bağlama işlemlerinden sonra veri tabanına kayıt yapıyoruz.


            } catch (e: Exception) {
                e.printStackTrace()
            }

            val intent = Intent(this@ArtActivity,MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // bundan önceki bütün aktivitileri kapattık.
            startActivity(intent)

        }
    }

    fun selectImage(view: View) {

        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            //bu bloğa giriyorsa izin verilmemiştir
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@ArtActivity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
            //izin verilmediyse android işletim sistemi kendi karar verip bir daha izin isteyebilir.
                Snackbar.make(view, "Permission needed", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Give Permission",
                        View.OnClickListener {
                            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        }).show()
            else {
                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            // bu bloğa giriyorsa izin verilmiştir.
            val intentToGallery =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intentToGallery)
            //Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI > Galeriye gidip oradan bir resim seçeceğimizi belirttik.
        }

    }
    fun registerLauncher() {

        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) { // resim tam anlamıyla seçildi mi onu kontrol ediyoruz.
                    val intentFromResult = result.data
                    if (intentFromResult != null) {
                        val imageData = intentFromResult.data
                        //binding.imageView.setImageURI(imageData)
                        if (imageData != null) {
                            try {
                                if (Build.VERSION.SDK_INT >= 28) {
                                    val source = ImageDecoder.createSource(
                                        this@ArtActivity.contentResolver,
                                        imageData
                                    )
                                    selectedBitmap = ImageDecoder.decodeBitmap(source)
                                    binding.imageView.setImageBitmap(selectedBitmap)
                                } else {
                                    selectedBitmap = MediaStore.Images.Media.getBitmap(
                                        contentResolver,
                                        imageData
                                    )
                                    binding.imageView.setImageBitmap(selectedBitmap)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    val intentToGallery =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    activityResultLauncher.launch(intentToGallery)
                } else {
                    Toast.makeText(this@ArtActivity, "Permission denied!", Toast.LENGTH_LONG).show()
                }
            }

    }
}