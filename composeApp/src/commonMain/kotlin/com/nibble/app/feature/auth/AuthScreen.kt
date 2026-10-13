package com.nibble.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.Kicker
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.QuietButton
import com.nibble.app.core.design.SegmentedControl
import com.nibble.app.core.design.tap

/**
 * Sign in / create account. Optional by design — the reader never demands it; an account
 * exists so the same book follows you to the web app. Errors are PLAIN and verbatim (§11).
 */
@Composable
fun AuthScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    var mode by remember { mutableStateOf(0) }   // 0 = sign in · 1 = create account
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).tap { onEvent(Event.Back) }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.inkFaint, modifier = Modifier.size(20.dp))
            }
            Text("Your account", style = type.display.copy(fontSize = 30.sp), color = c.ink)
        }
        Text(
            "One account, one bookmark — this phone and the web app, same page.",
            color = c.inkFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        if (!state.authConfigured) {
            // PLAIN — never joke at someone who is stuck (§11).
            Text(
                "Accounts aren't set up in this build. Add the Firebase API key and rebuild.",
                color = c.inkMuted, fontSize = 13.5.sp,
            )
            return@Column
        }

        SegmentedControl(
            options = listOf("Sign in", "Create account"),
            selectedIndex = mode,
            onSelect = { mode = it; onEvent(Event.ClearAuthMessages) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(18.dp))

        Kicker("Email")
        Spacer(Modifier.size(6.dp))
        AuthField(
            value = email, onChange = { email = it }, hint = "you@somewhere.com",
            keyboard = KeyboardType.Email,
        )
        Spacer(Modifier.size(14.dp))

        Kicker("Password")
        Spacer(Modifier.size(6.dp))
        AuthField(
            value = password, onChange = { password = it },
            hint = if (mode == 1) "At least 6 characters" else "Your password",
            keyboard = KeyboardType.Password,
            transformation = PasswordVisualTransformation(),
        )

        state.authError?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, color = c.clayPress, fontSize = 12.5.sp)
        }
        state.authNotice?.let {
            Spacer(Modifier.size(12.dp))
            Text(it, color = c.moss, fontSize = 12.5.sp)
        }

        Spacer(Modifier.size(20.dp))
        ClayButton(
            text = when {
                state.authBusy -> "One moment…"
                mode == 0 -> "Sign in"
                else -> "Create account"
            },
            enabled = !state.authBusy,
            onClick = { onEvent(if (mode == 0) Event.SignIn(email, password) else Event.SignUp(email, password)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode == 0) {
            Spacer(Modifier.size(6.dp))
            QuietButton("Forgot password? Email me a reset", onClick = { onEvent(Event.SendPasswordReset(email)) })
        }
        Spacer(Modifier.size(16.dp))
        Text(
            "Your reading stays on this device. The account only proves you're you — sync arrives with the web app.",
            color = c.inkFaint, fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun AuthField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboard: KeyboardType,
    transformation: VisualTransformation = VisualTransformation.None,
) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.rule, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = c.inkFaint, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = type.body.copy(fontSize = 14.sp, color = c.ink),
            cursorBrush = SolidColor(c.clay),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
