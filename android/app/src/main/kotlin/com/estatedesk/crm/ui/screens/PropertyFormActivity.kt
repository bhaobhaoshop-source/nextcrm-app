package com.estatedesk.crm.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.estatedesk.crm.R
import com.estatedesk.crm.core.Async
import com.estatedesk.crm.core.BaseActivity
import com.estatedesk.crm.core.Di
import com.estatedesk.crm.core.Files
import com.estatedesk.crm.core.Ui
import com.estatedesk.crm.data.Property
import com.estatedesk.crm.data.PropertyPhoto

class PropertyFormActivity : BaseActivity() {

    private var propertyId = 0L
    private lateinit var title: EditText
    private lateinit var price: EditText
    private lateinit var location: EditText
    private lateinit var areaName: EditText
    private lateinit var sizeValue: EditText
    private lateinit var bedrooms: EditText
    private lateinit var bathrooms: EditText
    private lateinit var floors: EditText
    private lateinit var ownerName: EditText
    private lateinit var ownerPhone: EditText
    private lateinit var description: EditText
    private var type = ""
    private var saleRent = "Sale"
    private var status = "Available"
    private var condition = ""
    private var sizeUnit = "Marla"
    private var furnished = false
    private var selectedTags = mutableListOf<String>()
    private var photos = mutableListOf<PropertyPhoto>()
    private var photosHost: LinearLayout? = null

    override fun build() {
        propertyId = intent.getLongExtra("id", 0)
        topBar(if (propertyId == 0L) getString(R.string.property_new) else getString(R.string.property_edit))
        if (propertyId > 0) loadExisting() else render()
    }

    private fun loadExisting() {
        Async.db({
            val p = Di.store.properties.byId(propertyId) ?: return@db null
            Pair(p, Di.store.properties.photos(propertyId))
        }) { t ->
            if (t == null || isFinishing) return@db
            val (p, ph) = t
            photos = ph.toMutableList()
            render()
            title.setText(p.title); price.setText(if (p.price > 0) p.price.toString() else "")
            location.setText(p.location); areaName.setText(p.areaName)
            sizeValue.setText(if (p.sizeValue > 0) p.sizeValue.toString() else "")
            bedrooms.setText(if (p.bedrooms > 0) p.bedrooms.toString() else "")
            bathrooms.setText(if (p.bathrooms > 0) p.bathrooms.toString() else "")
            floors.setText(if (p.floors > 0) p.floors.toString() else "")
            ownerName.setText(p.ownerName); ownerPhone.setText(p.ownerPhone)
            description.setText(p.description)
            type = p.type; saleRent = p.saleRent; status = p.status
            condition = p.condition; sizeUnit = p.sizeUnit; furnished = p.furnished
            selectedTags = p.tagList().toMutableList()
        }
    }

    private fun render() {
        title = Ui.input(this@PropertyFormActivity, getString(R.string.property_title))
        add(Ui.field(this, getString(R.string.property_title), title))

        val kindRow = Ui.hbox(this)
        kindRow.addView(Ui.pickField(this, getString(R.string.property_sale_rent), saleRent) {
            Ui.pick(this, getString(R.string.property_sale_rent),
                listOf(getString(R.string.property_sale), getString(R.string.property_rent))) { i ->
                saleRent = listOf("Sale", "Rent")[i]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 8) })
        kindRow.addView(Ui.pickField(this, getString(R.string.property_type),
            type.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.property_type),
                listOf(getString(R.string.flt_any)) + Di.store.propertyTypes()) { i ->
                type = if (i == 0) "" else Di.store.propertyTypes()[i - 1]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(kindRow)

        price = Ui.input(this@PropertyFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        add(Ui.field(this, getString(R.string.property_price), price))

        location = Ui.input(this@PropertyFormActivity, getString(R.string.contact_city))
        areaName = Ui.input(this@PropertyFormActivity, getString(R.string.contact_area))
        val locRow = Ui.hbox(this)
        locRow.addView(Ui.field(this, getString(R.string.contact_city), location),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 8) })
        locRow.addView(Ui.field(this, getString(R.string.contact_area), areaName),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(locRow)

        val sizeRow = Ui.hbox(this)
        sizeValue = Ui.input(this@PropertyFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        sizeRow.addView(Ui.field(this, getString(R.string.property_size), sizeValue),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 8) })
        sizeRow.addView(Ui.pickField(this, "Unit", sizeUnit) {
            Ui.pick(this, "Unit", listOf("Marla", "Kanal", "Sq Ft", "Sq Yd", "Sq M", "Acre")) { i ->
                sizeUnit = listOf("Marla", "Kanal", "Sq Ft", "Sq Yd", "Sq M", "Acre")[i]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(sizeRow)

        val bedRow = Ui.hbox(this)
        bedrooms = Ui.input(this@PropertyFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        bathrooms = Ui.input(this@PropertyFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        floors = Ui.input(this@PropertyFormActivity, "0", inputType = InputType.TYPE_CLASS_NUMBER)
        bedRow.addView(Ui.field(this, getString(R.string.property_bedrooms), bedrooms),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 6) })
        bedRow.addView(Ui.field(this, getString(R.string.property_bathrooms), bathrooms),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 6) })
        bedRow.addView(Ui.field(this, getString(R.string.property_floors), floors),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(bedRow)

        val condRow = Ui.hbox(this)
        condRow.addView(Ui.pickField(this, getString(R.string.property_condition),
            condition.ifBlank { getString(R.string.flt_any) }) {
            Ui.pick(this, getString(R.string.property_condition),
                listOf(getString(R.string.flt_any), "New", "Excellent", "Good", "Needs Work")) { i ->
                condition = if (i == 0) "" else listOf("New", "Excellent", "Good", "Needs Work")[i - 1]
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 8) })
        val furnishedBox = CheckBox(this).apply {
            text = getString(R.string.property_furnished)
            setTextColor(p.textPrimary)
            buttonTintList = android.content.res.ColorStateList.valueOf(p.primary)
            isChecked = furnished
            setOnCheckedChangeListener { _, b -> furnished = b }
        }
        condRow.addView(furnishedBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(condRow)

        add(Ui.pickField(this, getString(R.string.property_status), status) {
            Ui.pick(this, getString(R.string.property_status),
                listOf("Available", "Reserved", "Sold", "Rented", "Off Market", "Pending")) { i ->
                status = listOf("Available", "Reserved", "Sold", "Rented", "Off Market", "Pending")[i]
            }
        })

        val ownerRow = Ui.hbox(this)
        ownerName = Ui.input(this@PropertyFormActivity, getString(R.string.property_owner))
        ownerPhone = Ui.input(this@PropertyFormActivity, getString(R.string.property_owner_contact), inputType = InputType.TYPE_CLASS_PHONE)
        ownerRow.addView(Ui.field(this, getString(R.string.property_owner), ownerName),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = Ui.dp(this@PropertyFormActivity, 8) })
        ownerRow.addView(Ui.field(this, getString(R.string.property_owner_contact), ownerPhone),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(ownerRow)

        // tags
        add(Ui.label(this@PropertyFormActivity, getString(R.string.property_tags)))
        val allTags = Di.store.tags()
        if (allTags.isNotEmpty()) {
            val tagWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            var row = Ui.hbox(this)
            tagWrap.addView(row)
            allTags.forEachIndexed { i, t ->
                lateinit var c: android.widget.TextView
                c = Ui.chip(this@PropertyFormActivity, t, selectedTags.contains(t)) {
                    if (selectedTags.contains(t)) selectedTags.remove(t) else selectedTags.add(t)
                    c.isSelected = selectedTags.contains(t)
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = Ui.dp(this@PropertyFormActivity, 8); lp.bottomMargin = Ui.dp(this@PropertyFormActivity, 8)
                if (i % 3 == 0 && i > 0) {
                    row = Ui.hbox(this)
                    tagWrap.addView(row)
                }
                row.addView(c, lp)
            }
            add(tagWrap)
        }

        // photos
        add(Ui.label(this@PropertyFormActivity, getString(R.string.property_photos)))
        photosHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(photosHost!!)
        add(Ui.btn(this@PropertyFormActivity, "+ " + getString(R.string.property_add_photos), Ui.Btn.SECONDARY) { pickPhotos() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@PropertyFormActivity, 14)
            })
        renderPhotoThumbs()

        description = Ui.input(this@PropertyFormActivity, getString(R.string.property_description), multiline = true)
        add(Ui.field(this, getString(R.string.property_description), description))

        add(Ui.spacer(this, 8))
        add(Ui.btn(this@PropertyFormActivity, getString(R.string.save), Ui.Btn.PRIMARY) { save() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = Ui.dp(this@PropertyFormActivity, 40)
            })
    }

    private fun renderPhotoThumbs() {
        val host = photosHost ?: return
        host.removeAllViews()
        var row = Ui.hbox(this)
        host.addView(row)
        photos.forEachIndexed { i, ph ->
            if (i % 4 == 0 && i > 0) {
                row = Ui.hbox(this)
                host.addView(row)
            }
            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setBackgroundColor(p.surface2)
            img.layoutParams = LinearLayout.LayoutParams(Ui.dp(this@PropertyFormActivity, 68), Ui.dp(this@PropertyFormActivity, 68))
            Async.io({
                try {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                    android.graphics.BitmapFactory.decodeFile(ph.path, o)
                } catch (t: Throwable) { null }
            }) { bmp -> if (bmp != null) img.setImageBitmap(bmp) }
            img.setOnClickListener {
                Ui.alert(this@PropertyFormActivity, getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_msg), getString(R.string.delete)) {
                    photos.removeAt(i)
                    Async.write({ Di.store.properties.deletePhoto(ph.id) })
                    renderPhotoThumbs()
                }
            }
            val lp = LinearLayout.LayoutParams(Ui.dp(this@PropertyFormActivity, 68), Ui.dp(this@PropertyFormActivity, 68))
            lp.rightMargin = Ui.dp(this@PropertyFormActivity, 8)
            lp.bottomMargin = Ui.dp(this@PropertyFormActivity, 8)
            row.addView(img, lp)
        }
    }

    private fun pickPhotos() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 11)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == Activity.RESULT_OK && data != null) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
            }
            data.data?.let { uris.add(it) }
            Async.io({
                uris.mapNotNull { uri ->
                    Files.copyIn(this, uri, Files.photosDir(this), "photo")?.let { path ->
                        PropertyPhoto(0, propertyId, path.absolutePath, "", 0)
                    }
                }
            }) { newPhotos ->
                if (newPhotos.isNullOrEmpty()) {
                    snack(getString(R.string.error_generic))
                    return@io
                }
                if (propertyId > 0) {
                    Async.db({
                        newPhotos.map { ph ->
                            ph.copy(id = Di.store.properties.addPhoto(propertyId, ph.path))
                        }
                    }) { withIds ->
                        photos.addAll(withIds ?: newPhotos)
                        renderPhotoThumbs()
                    }
                } else {
                    photos.addAll(newPhotos)
                    renderPhotoThumbs()
                }
            }
        }
    }

    private fun save() {
        val t = title.text.toString().trim()
        if (t.isBlank() && areaName.text.toString().isBlank() && location.text.toString().isBlank()) {
            snack(getString(R.string.error_required)); return
        }
        val prop = Property(
            id = propertyId, title = t,
            type = type, saleRent = saleRent,
            price = price.text.toString().toLongOrNull() ?: 0,
            currency = Di.store.currency().code,
            location = location.text.toString().trim(),
            areaName = areaName.text.toString().trim(),
            sizeValue = sizeValue.text.toString().toDoubleOrNull() ?: 0.0,
            sizeUnit = sizeUnit,
            bedrooms = bedrooms.text.toString().toIntOrNull() ?: 0,
            bathrooms = bathrooms.text.toString().toIntOrNull() ?: 0,
            floors = floors.text.toString().toIntOrNull() ?: 0,
            condition = condition, furnished = furnished,
            ownerName = ownerName.text.toString().trim(),
            ownerPhone = ownerPhone.text.toString().trim(),
            status = status,
            description = description.text.toString().trim(),
            tags = selectedTags.joinToString(",")
        )
        Async.write({
            val id = Di.store.properties.save(prop)
            photos.filter { it.id == 0L }.forEach { ph ->
                Di.store.properties.addPhoto(id, ph.path)
            }
        }, {
            snack(getString(R.string.saved))
            setResult(Activity.RESULT_OK)
            finish()
        }, {
            snack(getString(R.string.error_save))
        })
    }
}
