package com.example.nfcapp

import android.nfc.Tag
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.nfcapp.databinding.ActivityBinder
import com.example.nfcapp.databinding.FragmentBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainFragment : Fragment, CompoundButton.OnCheckedChangeListener {

    companion object {
        private val TAG : String = MainFragment::class.java.getSimpleName()

        public fun newInstance() : MainFragment = MainFragment()
    }

    private var binder : FragmentBinder? = null
    private var binderActivity : ActivityBinder? = null
    //private val viewModel : MainViewModel by lazy { ViewModelProvider(requireActivity()).get(MainViewModel::class.java) }
    private val viewModel : MainViewModel by viewModels<MainViewModel>()

    private val nfcSharedViewModel: NfcSharedViewModel by activityViewModels() // Use activityViewModels to share with activity

    constructor() {

    }

    override fun onCreateView(inflater : LayoutInflater, container : ViewGroup?, savedInstanceState : Bundle?) : View? {
        binder = DataBindingUtil.inflate(inflater, R.layout.fragment_main,container,false)
        binder?.setViewModel(viewModel)
        binder?.setLifecycleOwner(this@MainFragment)
        return binder?.root ?: super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        // New button listener
        val writeButton = binderActivity?.writeButton
        writeButton?.setOnClickListener {
            // Example: Update the keg status to "filled"
            viewModel.setKegStatus("filled")

            nfcSharedViewModel.nfcTag.value?.let { tag ->
                viewModel.writeKeyStatusToTag(tag, viewModel.getKegStatus()) // Use the stored tag
            }

            nfcSharedViewModel.nfcTag.observe(viewLifecycleOwner) { tag ->
                // Update UI or take actions based on the new tag
            }
        }
        Coroutines.main(this@MainFragment, { scope ->
            scope.launch( block = { binder?.getViewModel()?.observeToast()?.collectLatest ( action = { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }) })
            scope.launch( block = { binder?.getViewModel()?.observeTag()?.collectLatest ( action = { tag ->
                binder?.textViewExplanation?.setText(tag)
            }) })
        })
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCheckedChanged(buttonView : CompoundButton?, isChecked : Boolean) {
        if (buttonView == binder?.toggleButton)
            viewModel.onCheckNFC(isChecked)
    }
}