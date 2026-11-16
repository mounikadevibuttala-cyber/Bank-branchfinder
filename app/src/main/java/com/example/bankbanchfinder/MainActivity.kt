package com.example.bankbanchfinder

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.bankbanchfinder.data.local.AppDatabase
import com.example.bankbanchfinder.data.model.BranchResponse
import com.example.bankbanchfinder.data.network.RazorpayApiService
import com.example.bankbanchfinder.data.repository.BranchRepository
import com.example.bankbanchfinder.data.repository.ResultState
import com.example.bankbanchfinder.databinding.ActivityMainBinding
import com.example.bankbanchfinder.ui.FavouritesAdapter
import com.example.bankbanchfinder.ui.MainViewModel
import com.example.bankbanchfinder.ui.ViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.chip.Chip
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var favouritesAdapter: FavouritesAdapter

    private var currentPhotoUri: Uri? = null
    private var currentBranchResponse: BranchResponse? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    firebaseAuthWithGoogle(account)
                }
            } catch (e: ApiException) {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
                if (googleAccount != null) {
                    firebaseAuthWithGoogle(googleAccount)
                } else {
                    Toast.makeText(
                        this,
                        "Google Sign-In failed (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && currentPhotoUri != null) {
                viewModel.processImageUri(this, currentPhotoUri!!)
            } else {
                Toast.makeText(this, "Camera capture cancelled", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                viewModel.processImageUri(this, uri)
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to scan cheques", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewModel()
        setupGoogleSignIn()
        setupFavouritesRecyclerView()
        setupClickListeners()
        observeViewModel()
        checkCurrentUser()
    }

    private fun setupViewModel() {
        val database = AppDatabase.getDatabase(this)
        val apiService = RazorpayApiService.create()
        val repository = BranchRepository(apiService, database.favouriteBranchDao())
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private fun setupGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id_placeholder)
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (!webClientId.isNullOrEmpty() && !webClientId.contains("placeholder")) {
            builder.requestIdToken(webClientId)
        }

        val gso = builder.build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun checkCurrentUser() {
        try {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                viewModel.setUser(firebaseUser)
                updateUserUi(firebaseUser.displayName, firebaseUser.email, firebaseUser.photoUrl)
            } else {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
                if (googleAccount != null) {
                    updateUserUi(googleAccount.displayName, googleAccount.email, googleAccount.photoUrl)
                }
            }
        } catch (e: Exception) {
            val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
            if (googleAccount != null) {
                updateUserUi(googleAccount.displayName, googleAccount.email, googleAccount.photoUrl)
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val idToken = account.idToken
        updateUserUi(account.displayName, account.email, account.photoUrl)

        if (!idToken.isNullOrEmpty()) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val user = FirebaseAuth.getInstance().currentUser
                            viewModel.setUser(user)
                            Toast.makeText(this, "Signed in as ${user?.displayName ?: account.displayName ?: "User"}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Signed in as ${account.displayName ?: account.email ?: "User"}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (e: Exception) {
                Toast.makeText(this, "Signed in as ${account.displayName ?: account.email ?: "User"}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Signed in as ${account.displayName ?: account.email ?: "User"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUserUi(displayName: String?, email: String?, photoUrl: Uri?) {
        if (!displayName.isNullOrEmpty() || !email.isNullOrEmpty()) {
            binding.layoutUserInfo.visibility = View.VISIBLE
            binding.btnSignIn.visibility = View.GONE
            binding.tvUserName.text = displayName ?: email ?: "User"

            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .into(binding.ivUserAvatar)
            } else {
                binding.ivUserAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
        } else {
            binding.layoutUserInfo.visibility = View.GONE
            binding.btnSignIn.visibility = View.VISIBLE
        }
    }

    private fun setupFavouritesRecyclerView() {
        favouritesAdapter = FavouritesAdapter(
            onItemClick = { favBranch ->
                binding.etIfscInput.setText(favBranch.ifsc)
                viewModel.searchIfsc(favBranch.ifsc)
            },
            onCopyClick = { ifsc ->
                copyToClipboard(ifsc)
            },
            onDeleteClick = { ifsc ->
                viewModel.removeFavourite(ifsc)
                Toast.makeText(this, "Removed $ifsc from favourites", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvFavourites.adapter = favouritesAdapter
    }

    private fun setupClickListeners() {
        binding.btnSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.btnSignOut.setOnClickListener {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (ignored: Exception) {}
            googleSignInClient.signOut().addOnCompleteListener {
                viewModel.setUser(null)
                updateUserUi(null, null, null)
                Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSearch.setOnClickListener {
            val ifsc = binding.etIfscInput.text.toString()
            viewModel.searchIfsc(ifsc)
        }

        binding.etIfscInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchIfsc(binding.etIfscInput.text.toString())
                true
            } else {
                false
            }
        }

        binding.btnScanCheque.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnPickGallery.setOnClickListener {
            pickGalleryLauncher.launch("image/*")
        }

        binding.btnCopyIfsc.setOnClickListener {
            val ifsc = currentBranchResponse?.ifsc
            if (!ifsc.isNullOrEmpty()) {
                copyToClipboard(ifsc)
            }
        }

        binding.btnFavourite.setOnClickListener {
            currentBranchResponse?.let { branch ->
                viewModel.toggleFavourite(branch)
            }
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(currentPhotoUri!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating camera file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("CHEQUE_${timeStamp}_", ".jpg", storageDir)
    }

    private fun observeViewModel() {
        viewModel.user.observe(this) { firebaseUser ->
            if (firebaseUser != null) {
                updateUserUi(firebaseUser.displayName, firebaseUser.email, firebaseUser.photoUrl)
            } else {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(this)
                if (googleAccount != null) {
                    updateUserUi(googleAccount.displayName, googleAccount.email, googleAccount.photoUrl)
                } else {
                    updateUserUi(null, null, null)
                }
            }
        }

        viewModel.searchResult.observe(this) { result ->
            when (result) {
                is ResultState.Loading -> {
                    binding.layoutLoading.visibility = View.VISIBLE
                    binding.cardError.visibility = View.GONE
                    binding.cardDetails.visibility = View.GONE
                }
                is ResultState.Success -> {
                    binding.layoutLoading.visibility = View.GONE
                    binding.cardError.visibility = View.GONE
                    displayBranchDetails(result.data)
                }
                is ResultState.Error -> {
                    binding.layoutLoading.visibility = View.GONE
                    binding.cardDetails.visibility = View.GONE
                    binding.cardError.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = result.message
                }
                null -> {
                    binding.layoutLoading.visibility = View.GONE
                    binding.cardError.visibility = View.GONE
                    binding.cardDetails.visibility = View.GONE
                }
            }
        }

        viewModel.ocrMessage.observe(this) { msg ->
            if (!msg.isNullOrEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.clearOcrMessage()
            }
        }

        viewModel.isCurrentFavourite.observe(this) { isFav ->
            if (isFav) {
                binding.btnFavourite.setImageResource(android.R.drawable.btn_star_big_on)
            } else {
                binding.btnFavourite.setImageResource(android.R.drawable.btn_star_big_off)
            }
        }

        viewModel.favourites.observe(this) { list ->
            favouritesAdapter.submitList(list)
            if (list.isNullOrEmpty()) {
                binding.tvEmptyFavourites.visibility = View.VISIBLE
                binding.rvFavourites.visibility = View.GONE
            } else {
                binding.tvEmptyFavourites.visibility = View.GONE
                binding.rvFavourites.visibility = View.VISIBLE
            }
        }
    }

    private fun displayBranchDetails(branch: BranchResponse) {
        currentBranchResponse = branch
        binding.cardDetails.visibility = View.VISIBLE

        binding.tvDetailBank.text = branch.bank ?: "Indian oversall bank"
        binding.tvDetailBranch.text = "${branch.branch ?: "Main Branch"}, ${branch.city ?: "vizinagaram"}"
        binding.tvDetailIfsc.text = "IFSC: ${branch.ifsc ?: "AC231390021"}"
        binding.etIfscInput.setText(branch.ifsc ?: "AC231390021")

        binding.tvDetailMicr.text = "MICR: ${branch.micr ?: "N/A"}"
        binding.tvDetailContact.text = "Contact: ${branch.contact ?: "mounika devi"}"
        binding.tvDetailAddress.text = "Address: ${branch.address ?: "ayinada"}"
        binding.tvDetailCityState.text = "City: ${branch.city ?: "vizinagaram"} | State: ${branch.state ?: "Andhra pradesh"}"

        setupServiceChip(binding.chipUpi, "UPI", branch.upi == true)
        setupServiceChip(binding.chipNeft, "NEFT", branch.neft == true)
        setupServiceChip(binding.chipRtgs, "RTGS", branch.rtgs == true)
        setupServiceChip(binding.chipImps, "IMPS", branch.imps == true)
    }

    private fun setupServiceChip(chip: Chip, label: String, isSupported: Boolean) {
        chip.text = label
        if (isSupported) {
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.chip_supported_bg))
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.chip_supported_stroke))
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_supported_text))
            chip.chipIcon = ContextCompat.getDrawable(this, android.R.drawable.checkbox_on_background)
            chip.isChipIconVisible = true
        } else {
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.chip_unsupported_bg))
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.chip_unsupported_stroke))
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_unsupported_text))
            chip.isChipIconVisible = false
        }
    }

    private fun copyToClipboard(ifsc: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("IFSC Code", ifsc)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied $ifsc to clipboard", Toast.LENGTH_SHORT).show()
    }
}
