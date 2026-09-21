package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ChatGPT Inspired Light Theme Palette
val ChatGPTLightBackground = Color(0xFFFFFFFF)
val ChatGPTLightCard = Color(0xFFFFFFFF)
val ChatGPTLightSurfaceVariant = Color(0xFFF4F4F4) // clean soft grey pill containers / user bubbles
val ChatGPTLightBorder = Color(0xFFE5E5E5) // subtle hairline border
val ChatGPTLightBorderVariant = Color(0xFFECECF1)
val ChatGPTLightTextPrimary = Color(0xFF0D0D0D) // deep charcoal black
val ChatGPTLightTextSecondary = Color(0xFF5D5D5D) // neutral medium gray
val ChatGPTLightAccent = Color(0xFF10A37F) // iconic ChatGPT emerald green
val ChatGPTLightSelectedBg = Color(0xFFE8F5EE) // subtle emerald container tint

// Warm Cream Editorial Palette (Legacy references mapped to ChatGPT Light)
val NovaLightBackground = ChatGPTLightBackground
val NovaLightCard = ChatGPTLightCard
val NovaLightSurfaceVariant = ChatGPTLightSurfaceVariant
val NovaLightBorder = ChatGPTLightBorder
val NovaLightTextPrimary = ChatGPTLightTextPrimary
val NovaLightTextSecondary = ChatGPTLightTextSecondary
val NovaLightAccent = ChatGPTLightAccent
val NovaLightSelectedBg = ChatGPTLightSelectedBg

// Near-Black Editorial Palette (Dark)
val NovaDarkBackground = Color(0xFF121212)
val NovaDarkCard = Color(0xFF1B1B1B)
val NovaDarkSurfaceVariant = Color(0xFF242424) // chips
val NovaDarkBorder = Color(0xFF2E2E2E) // hairline
val NovaDarkTextPrimary = Color(0xFFEDE8E0)
val NovaDarkTextSecondary = Color(0xFFA39B8F)
val NovaDarkAccent = Color(0xFFD97B6C) // lightened for contrast
val NovaDarkUserBubble = Color(0xFF2A2A21)
val NovaGoldLabel = Color(0xFFD9B95C) // gold "YOU" label

// Active semantic references (Defaulting to Warm Cream Light)
val NovaBackground = NovaLightBackground
val NovaCard = NovaLightCard
val NovaSurfaceVariant = NovaLightSurfaceVariant
val NovaBorder = NovaLightBorder
val NovaTextPrimary = NovaLightTextPrimary
val NovaTextSecondary = NovaLightTextSecondary
val NovaTextTertiary = NovaLightTextSecondary
val NovaAccent = NovaLightAccent
val NovaSelectedBg = NovaLightSelectedBg

// Status colors (editorial, muted, non-neon)
val NovaGreen = Color(0xFF3F6E50) // muted editorial green
val NovaOrange = Color(0xFFB06F30) // muted warm amber
val NovaRed = NovaLightAccent // terracotta for error/destructive

// Backward compatibility aliases
val NovaDarkSurface = NovaLightCard
val NovaDarkSurfaceHighlight = NovaLightSurfaceVariant
val NovaCyan = NovaLightAccent
val NovaViolet = NovaLightAccent
val NovaPink = NovaLightAccent
val NovaBorderActive = NovaLightAccent
