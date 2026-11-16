package com.example.bankbanchfinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.bankbanchfinder.data.local.FavouriteBranch
import com.example.bankbanchfinder.data.model.BranchResponse
import com.example.bankbanchfinder.data.repository.BranchRepository
import com.example.bankbanchfinder.data.repository.ResultState
import com.example.bankbanchfinder.util.IfscExtractor
import com.google.firebase.auth.FirebaseUser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

class MainViewModel(private val repository: BranchRepository) : ViewModel() {

    private val _searchResult = MutableLiveData<ResultState<BranchResponse>?>()
    val searchResult: LiveData<ResultState<BranchResponse>?> = _searchResult

    private val _ocrMessage = MutableLiveData<String?>()
    val ocrMessage: LiveData<String?> = _ocrMessage

    private val _user = MutableLiveData<FirebaseUser?>()
    val user: LiveData<FirebaseUser?> = _user

    val favourites: LiveData<List<FavouriteBranch>> = repository.allFavourites.asLiveData()

    private val _currentIfsc = MutableLiveData<String>("")
    val isCurrentFavourite: LiveData<Boolean> = _currentIfsc.switchMap { ifsc ->
        if (ifsc.isEmpty()) {
            MutableLiveData(false)
        } else {
            repository.isFavourite(ifsc).asLiveData()
        }
    }

    fun setUser(user: FirebaseUser?) {
        _user.value = user
    }

    fun searchIfsc(ifsc: String) {
        val cleanIfsc = ifsc.trim().uppercase()
        if (cleanIfsc.isEmpty()) {
            _searchResult.value = ResultState.Error("Please enter an IFSC code.")
            return
        }

        if (!IfscExtractor.isValidIfsc(cleanIfsc)) {
            _searchResult.value = ResultState.Error("Invalid IFSC format. Must be 11 characters, e.g. SBIN0000001")
            return
        }

        _currentIfsc.value = cleanIfsc
        _searchResult.value = ResultState.Loading

        viewModelScope.launch {
            val result = repository.getBranchDetails(cleanIfsc)
            _searchResult.value = result
        }
    }

    fun processImageUri(context: Context, uri: Uri) {
        _searchResult.value = ResultState.Loading
        try {
            val image = InputImage.fromFilePath(context, uri)
            runTextRecognition(image)
        } catch (e: Exception) {
            _searchResult.value = ResultState.Error("Failed to read image: ${e.localizedMessage}")
        }
    }

    fun processImageBitmap(bitmap: Bitmap) {
        _searchResult.value = ResultState.Loading
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            runTextRecognition(image)
        } catch (e: Exception) {
            _searchResult.value = ResultState.Error("Failed to process image: ${e.localizedMessage}")
        }
    }

    private fun runTextRecognition(image: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val detectedIfsc = IfscExtractor.extractIfsc(fullText)
                if (detectedIfsc != null) {
                    _ocrMessage.postValue("IFSC Found in image: $detectedIfsc")
                    searchIfsc(detectedIfsc)
                } else {
                    _searchResult.postValue(
                        ResultState.Error(
                            "No valid IFSC code found in the cheque image.\nPlease try manual entry.",
                            isNotFound = true
                        )
                    )
                }
            }
            .addOnFailureListener { e ->
                _searchResult.postValue(ResultState.Error("Text recognition failed: ${e.localizedMessage}"))
            }
    }

    fun toggleFavourite(branchResponse: BranchResponse) {
        val ifsc = branchResponse.ifsc ?: return
        viewModelScope.launch {
            val fav = FavouriteBranch(
                ifsc = ifsc,
                bank = branchResponse.bank ?: "Unknown Bank",
                branch = branchResponse.branch ?: "Unknown Branch",
                address = branchResponse.address ?: "",
                city = branchResponse.city ?: "",
                state = branchResponse.state ?: "",
                contact = branchResponse.contact ?: "",
                micr = branchResponse.micr ?: "",
                upi = branchResponse.upi ?: false,
                neft = branchResponse.neft ?: false,
                rtgs = branchResponse.rtgs ?: false,
                imps = branchResponse.imps ?: false
            )
            val isFav = isCurrentFavourite.value ?: false
            if (isFav) {
                repository.removeFavourite(ifsc)
            } else {
                repository.addFavourite(fav)
            }
        }
    }

    fun removeFavourite(ifsc: String) {
        viewModelScope.launch {
            repository.removeFavourite(ifsc)
        }
    }

    fun clearOcrMessage() {
        _ocrMessage.value = null
    }
}
