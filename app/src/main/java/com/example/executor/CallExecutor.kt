package com.example.executor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.SecurePreferences
import com.example.model.ActionCommand
import com.example.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallExecutor(
    private val context: Context,
    private val preferences: SecurePreferences
) : ActionExecutor {

    private data class ContactItem(val name: String, val number: String)

    override suspend fun execute(command: ActionCommand, isConfirmed: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        val target = command.target.trim()
        if (target.isBlank()) {
            return@withContext ExecutionResult.Error("No contact name or phone number provided")
        }

        // Check if target is already a literal phone number
        val isDirectPhoneNumber = target.matches(Regex("^[+]?[0-9\\-\\s()]{3,}$"))

        val resolvedContact: ContactItem? = if (isDirectPhoneNumber) {
            ContactItem(name = target, number = target)
        } else {
            // Need READ_CONTACTS permission to search device contacts
            val hasReadContacts = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasReadContacts) {
                return@withContext ExecutionResult.MissingPermission(
                    permission = Manifest.permission.READ_CONTACTS,
                    explanation = "Kuchu Puchu needs permission to read your contacts to find '${target}'."
                )
            }

            findContact(target)
        }

        if (resolvedContact == null) {
            return@withContext ExecutionResult.Error("Could not find contact matching '$target' in your phone contacts.")
        }

        // Before executing calls, show a confirmation card (action + target + Execute/Cancel buttons)
        if (!isConfirmed) {
            return@withContext ExecutionResult.RequiresConfirmation(
                command = command,
                targetResolved = "${resolvedContact.name} (${resolvedContact.number})",
                details = "Call ${resolvedContact.name} at ${resolvedContact.number}"
            )
        }

        // Confirmed: proceed to execute
        try {
            val autoCall = preferences.isAutoCallEnabled
            val cleanedNumber = resolvedContact.number.replace(Regex("[^0-9+]"), "")
            val uri = Uri.parse("tel:$cleanedNumber")

            if (autoCall) {
                val hasCallPhone = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasCallPhone) {
                    return@withContext ExecutionResult.MissingPermission(
                        permission = Manifest.permission.CALL_PHONE,
                        explanation = "Direct auto-call requires Phone Call permission. You can grant it, or toggle off auto-call in Settings for safe dialing."
                    )
                }

                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ExecutionResult.Success(
                    message = "Calling ${resolvedContact.name}",
                    details = "Direct call to ${resolvedContact.number}"
                )
            } else {
                // Safe default: ACTION_DIAL
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                ExecutionResult.Success(
                    message = "Opened dialer for ${resolvedContact.name}",
                    details = "Number: ${resolvedContact.number}"
                )
            }
        } catch (e: Exception) {
            Log.e("CallExecutor", "Failed to start phone action", e)
            ExecutionResult.Error("Failed to initiate call: ${e.localizedMessage}")
        }
    }

    private fun findContact(query: String): ContactItem? {
        val q = query.lowercase().trim()
        val contacts = mutableListOf<ContactItem>()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIdx != -1) it.getString(nameIdx).orEmpty() else ""
                    val num = if (numIdx != -1) it.getString(numIdx).orEmpty() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        contacts.add(ContactItem(name, num))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallExecutor", "Error reading contacts", e)
        }

        if (contacts.isEmpty()) return null

        val contactPairs = contacts.map { it.name to it.number }
        val matched = ContactMatcher.findBestContact(query, contactPairs)
        return matched?.let { ContactItem(it.name, it.number) }
    }
}
