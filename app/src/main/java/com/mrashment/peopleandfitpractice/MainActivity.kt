package com.mrashment.peopleandfitpractice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.people.v1.PeopleService
import com.google.api.services.people.v1.PeopleServiceScopes
import com.google.api.services.people.v1.model.Person
import com.mrashment.peopleandfitpractice.databinding.ActivityMainBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    val googleClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .requestScopes(
                    Scope(PeopleServiceScopes.USER_BIRTHDAY_READ), Scope(
                        PeopleServiceScopes.USERINFO_PROFILE
                    )
                )
                .requestProfile()
                .requestServerAuthCode(getString(R.string.client_id))
                .build()

        GoogleSignIn.getClient(this, gso)
    }


    lateinit var binding: ActivityMainBinding
    private val RC_SIGN_IN = 10
    private var isPersonGot = false
    private var person: Person? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignIn.setOnClickListener {
            if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                googleSignIn()
            } else {
                googleClient.revokeAccess()
                googleClient.signOut().addOnCompleteListener {
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                }
            }
        }


    }

    private fun googleSignIn() {
        startActivityForResult(googleClient.signInIntent, RC_SIGN_IN)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {

            val account = completedTask.getResult(ApiException::class.java)

            Toast.makeText(this, "Signed in", Toast.LENGTH_LONG).show()

            if (account?.serverAuthCode != null) {
                getPerson(account.serverAuthCode!!)
            } else {
                Log.e(TAG, "handleSignInResult: server auth code not available")
            }
        } catch (e: ApiException) {
            Toast.makeText(this, "Failed sign in: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun getPerson(serverAuthCode: String) {
        Log.d(TAG, "getPerson: serverAuthCode" + serverAuthCode)

        Single.fromCallable {
            val transport = NetHttpTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()

            val tokenResponse = GoogleAuthorizationCodeTokenRequest(
                    transport,
                    jsonFactory,
                    getString(R.string.client_id),
                    getString(R.string.client_secret),
                    serverAuthCode,
                    ""
            ).execute()

            val credential = GoogleCredential.Builder().apply {
                setClientSecrets(getString(R.string.client_id), getString(R.string.client_secret))
                setTransport(transport)
                setJsonFactory(jsonFactory)
            }.build()

            credential.setFromTokenResponse(tokenResponse)

            val peopleService = PeopleService.Builder(transport, jsonFactory, credential).apply {
                applicationName = getString(R.string.app_name)
            }.build()

            peopleService.people()["people/me"].setPersonFields("birthdays,genders").execute() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Person?> {
                override fun onSubscribe(d: Disposable?) {}
                override fun onSuccess(value: Person?) {
                    Log.d(TAG, "onSuccess: person = $value")
                    isPersonGot = true
                    person = value

                    binding.tvProfileInfo.text = ""
                    binding.tvProfileInfo.append("Birthday: " + person?.birthdays?.get(0)?.date?.let { it.year.toString() + "-" + it.month + "-" + it.day } + "\n")
                    binding.tvProfileInfo.append("Gender: " + person?.genders?.let { if (it.size > 0) it.get(0).formattedValue else "None" })
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "onError: ", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to get person" + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    isPersonGot = true
                }
            })

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }
}