package com.example.nfcapp

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData

class NfcSharedViewModel(application: Application) : AndroidViewModel(application) {
    val nfcTag: MutableLiveData<Tag?> = MutableLiveData(null)

    fun setNfcTag(tag: Tag) {
        nfcTag.value = tag
    }
}
