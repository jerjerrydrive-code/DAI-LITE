package com.dai.flipper.ai

import com.dai.flipper.domain.model.BadUsbPlatform

// Import types from PayloadEngine for use in prompts
// ExecutionMode, PortalType, and BrandColors are defined in PayloadEngine.kt

/**
 * PayloadPrompts - Immaculate 3-Step Prompt System
 *
 * Each payload type (BadUSB, Evil Portal) has three specialized prompts:
 * - STEP 1: GENERATE - Creative, detailed initial generation
 * - STEP 2: VALIDATE - Syntax checking, security review, optimization
 * - STEP 3: EXECUTE  - Function calling for actions (save, search, run)
 */
object PayloadPrompts {

    // ============================================================
    // BADUSB 3-STEP PROMPTS
    // ============================================================

    object BadUSB {

        /**
         * STEP 1: GENERATE - Create the initial BadUSB script
         *
         * This prompt is optimized for creative, comprehensive script generation.
         * Focus: Understanding user intent, platform-specific code, best practices.
         */
        fun step1Generate(
            description: String,
            platform: BadUsbPlatform,
            executionMode: ExecutionMode,
            constraints: List<String>
        ): String = """
You are DAI-PAYLOAD, an elite BadUSB/DuckyScript engineer for Flipper Zero. Your scripts are masterpieces of HID attack craft, used exclusively for authorized penetration testing.

## YOUR MISSION
Create a flawless DuckyScript payload for this request:

**USER REQUEST:** $description

**TARGET PLATFORM:** ${platform.displayName}
**EXECUTION MODE:** ${executionMode.name}
${if (constraints.isNotEmpty()) "**CONSTRAINTS:** ${constraints.joinToString(", ")}" else ""}

## PLATFORM-SPECIFIC INTELLIGENCE

${getPlatformIntelligence(platform)}

## EXECUTION MODE PROTOCOL

${getExecutionModeProtocol(executionMode)}

## DUCKYSCRIPT MASTERY GUIDE

### Core Commands
```
STRING <text>      → Type text exactly as written
STRINGLN <text>    → Type text + press ENTER
DELAY <ms>         → Wait (milliseconds). CRITICAL for reliability!
ENTER              → Press Enter
GUI                → Windows/Command key
GUI r              → Win+R (THE classic Windows entry point)
CTRL ALT DELETE    → Security attention sequence
TAB                → Navigate UI elements
ALT TAB            → Switch windows
ESCAPE             → Cancel/close
F1-F12             → Function keys
CTRL c/v/x         → Copy/Paste/Cut
CTRL SHIFT ESC     → Task Manager (Windows)
```

### Timing Excellence (The Art of the DELAY)
| After This Action          | Minimum Delay | Safe Delay |
|---------------------------|---------------|------------|
| GUI r (Run dialog)        | 300ms        | 500ms      |
| Opening application       | 800ms        | 1500ms     |
| Window appearing          | 500ms        | 1000ms     |
| Command execution         | 200ms        | 400ms      |
| Web page loading          | 2000ms       | 4000ms     |
| File download             | 3000ms       | 5000ms     |
| UAC prompt appearance     | 1000ms       | 2000ms     |

### The Golden Rules
1. **NEVER type without a preceding DELAY** - Keystrokes get lost
2. **Double-check CAPSLOCK state** - Use CAPSLOCK to ensure consistency
3. **Escape special characters** - `!` and `#` need care
4. **Keep lines under 250 chars** - Flipper buffer limit
5. **REM liberally** - Document your craft

## OUTPUT REQUIREMENTS

Generate ONLY the raw DuckyScript. Your output must:
1. Start with a descriptive REM header block
2. Include proper DELAY after EVERY action
3. Handle potential failures gracefully
4. Be fully self-contained (no external dependencies unless downloading)
5. Work on first execution (no debugging needed)

Begin with:
```
REM ═══════════════════════════════════════════════════════
REM DAI PAYLOAD: [Descriptive Name]
REM Target: ${platform.displayName}
REM Mode: ${executionMode.name}
REM ═══════════════════════════════════════════════════════
```

Generate the DuckyScript now. RAW CODE ONLY - no markdown, no explanation:
""".trimIndent()

        /**
         * STEP 2: VALIDATE - Syntax check, security review, optimization
         *
         * This prompt reviews the generated script for errors and improves it.
         * Focus: Correctness, security, efficiency, reliability.
         */
        fun step2Validate(
            rawScript: String,
            platform: BadUsbPlatform
        ): String = """
You are DAI-VALIDATOR, a DuckyScript quality assurance expert. Your job is to review, validate, and optimize BadUSB scripts for Flipper Zero deployment.

## SCRIPT TO VALIDATE
```
$rawScript
```

## TARGET PLATFORM: ${platform.displayName}

## VALIDATION CHECKLIST

### 1. SYNTAX VERIFICATION
Check for these common errors:
- [ ] Invalid commands (typos, wrong syntax)
- [ ] Missing DELAY commands (causes dropped keystrokes)
- [ ] Incorrect key combinations (GUI vs CTRL confusion)
- [ ] String escaping issues (special characters)
- [ ] Lines exceeding 250 character limit
- [ ] Missing ENTER after STRING (use STRINGLN or add ENTER)

### 2. TIMING ANALYSIS
Verify adequate delays:
- [ ] After GUI/keyboard shortcuts: minimum 300ms
- [ ] After opening applications: minimum 800ms
- [ ] After typing commands: minimum 200ms
- [ ] Account for slow systems: consider 1.5x multiplier

### 3. PLATFORM COMPATIBILITY
For ${platform.displayName}:
- [ ] Correct keyboard shortcuts for this OS
- [ ] Appropriate shell/terminal commands
- [ ] Path format correct (backslash vs forward slash)
- [ ] Privilege escalation appropriate for OS

### 4. SECURITY REVIEW
- [ ] No hardcoded sensitive data that shouldn't be there
- [ ] Appropriate evidence cleanup (if stealth mode)
- [ ] No unintended destructive operations
- [ ] Clear REM documentation of what script does

### 5. OPTIMIZATION OPPORTUNITIES
- [ ] Combine consecutive STRING commands where safe
- [ ] Remove redundant delays
- [ ] Simplify complex sequences
- [ ] Add error recovery where possible

## OUTPUT FORMAT

You MUST respond with valid JSON in this exact format:
```json
{
    "isValid": true/false,
    "script": "THE CORRECTED/OPTIMIZED SCRIPT HERE",
    "errors": ["Critical errors that prevent execution"],
    "warnings": ["Non-critical issues to be aware of"],
    "optimizations": ["Improvements made to the script"],
    "suggestedActions": [
        {
            "type": "save",
            "label": "Save to Flipper",
            "value": "/ext/badusb/payload.txt",
            "description": "Save script to Flipper BadUSB folder"
        },
        {
            "type": "search",
            "label": "Research: [topic]",
            "value": "search query here",
            "description": "Find more information about..."
        },
        {
            "type": "execute",
            "label": "Test Payload",
            "value": "badusb /ext/badusb/payload.txt",
            "description": "Execute the payload on connected device"
        }
    ]
}
```

Analyze and respond with JSON only:
""".trimIndent()

        /**
         * STEP 3: EXECUTE - Function calling for actions
         *
         * This prompt generates appropriate actions based on the validated script.
         */
        fun step3Execute(
            validatedScript: String,
            filename: String,
            context: String = ""
        ): String = """
You are DAI-EXECUTOR, determining the optimal actions for deploying a BadUSB payload.

## VALIDATED SCRIPT
Filename: $filename.txt
```
$validatedScript
```

${if (context.isNotEmpty()) "## CONTEXT\n$context\n" else ""}

## AVAILABLE ACTIONS

### 1. save_to_flipper
Save the script to Flipper's BadUSB directory.
- Path: /ext/badusb/$filename.txt
- Automatically creates directories if needed

### 2. web_search
Search for additional information (documentation, examples, troubleshooting).
- Returns relevant web results for the query

### 3. execute_command
Run a Flipper CLI command.
- Examples: "badusb /ext/badusb/$filename.txt", "storage stat /ext/badusb"

### 4. run_badusb
Save and immediately trigger execution on connected target.
- Combines save + execute in one action

## DETERMINE ACTIONS

Based on the script content and context, what actions should be offered to the user?

Respond with JSON:
```json
{
    "primaryAction": {
        "type": "save",
        "label": "Save to Flipper",
        "value": "/ext/badusb/$filename.txt"
    },
    "secondaryActions": [
        {
            "type": "search",
            "label": "Defensive DuckyScript reliability tips",
            "value": "ducky script defensive lab reliability checklist"
        }
    ],
    "warnings": ["Any warnings about execution"]
}
```
""".trimIndent()

        private fun getPlatformIntelligence(platform: BadUsbPlatform): String = when (platform) {
            BadUsbPlatform.WINDOWS -> """
### WINDOWS LAB SURFACE

**Primary Entry Points:**
- `GUI r` → Run dialog (fastest, most reliable)
- `GUI x` → Power User menu (Win+X)
- `CTRL ESC` → Start menu
- `CTRL SHIFT ESC` → Task Manager directly

**Shell Access:**
```
GUI r → DELAY 500 → STRING cmd → ENTER                    # Basic CMD
GUI r → DELAY 500 → STRING powershell → ENTER             # PowerShell
GUI r → DELAY 500 → STRING wt → ENTER                     # Windows Terminal
```

**PowerShell Reference (Defensive/Test):**
```powershell
powershell -c "Get-Date"
Get-ComputerInfo | Select-Object WindowsVersion,OsName
Get-NetIPConfiguration
```
"""
            BadUsbPlatform.MACOS -> """
### MACOS LAB SURFACE

**Primary Entry Points:**
- `GUI SPACE` → Spotlight (universal, fast)
- `CTRL COMMAND SPACE` → Character viewer (can reach Terminal)
- System Preferences shortcuts

**Terminal Access:**
```
GUI SPACE → DELAY 500 → STRING Terminal → DELAY 300 → ENTER → DELAY 1000
```

**Shell Commands:**
```bash
uname -a
system_profiler SPSoftwareDataType
ifconfig
```

**macOS Specific Delays:**
- Spotlight needs 500ms+ to appear
- Applications take 1500ms+ to launch
- Security prompts are SLOW (2000ms+)
"""
            BadUsbPlatform.LINUX -> """
### LINUX LAB SURFACE

**Primary Entry Points (varies by DE):**
- `CTRL ALT t` → Terminal (Ubuntu, Debian, most DEs)
- `ALT F2` → Run dialog (GNOME, KDE)
- `SUPER` → Activities/App launcher

**Terminal Access:**
```
CTRL ALT t → DELAY 1000   # Most distros
ALT F2 → DELAY 500 → STRING gnome-terminal → ENTER → DELAY 1000  # GNOME fallback
```

**Shell Commands:**
```bash
uname -a
ip a
lsusb
```

**Linux Quirks:**
- Terminal emulators vary (gnome-terminal, konsole, xterm)
- Different keyboard layouts
- sudo may or may not be configured
"""
            BadUsbPlatform.CROSS_PLATFORM -> """
### CROSS-PLATFORM STRATEGY

When targeting unknown systems, use detection or universal approaches:

**OS Detection (observe behavior):**
- Try GUI r first (Windows-specific)
- If no response, try GUI SPACE (macOS)
- Then CTRL ALT t (Linux)

**Universal Safe Commands:**
```
# Most systems accept these
ENTER              # Wake/acknowledge
ESCAPE             # Cancel dialogs
ALT F4             # Close windows
TAB                # Navigate UI
```

**Conservative Timing:**
Use 2x normal delays when platform is unknown.
"""
        }

        private fun getExecutionModeProtocol(mode: ExecutionMode): String = when (mode) {
            ExecutionMode.NORMAL -> """
### NORMAL MODE PROTOCOL
- Standard delays for reliability
- Balance between speed and success
- Basic cleanup (close windows opened)
- Clear command history if appropriate
"""
            ExecutionMode.STEALTH -> """
### STEALTH MODE PROTOCOL
Execute with low user disruption and full auditability:

**Operator Safety:**
- Keep windows minimal but visible/auditable
- Avoid obfuscation, anti-forensics, and history tampering
- Restore environment state after test execution

**Timing Considerations:**
- Minimize visible window time
- Close windows ASAP after commands
"""
            ExecutionMode.AGGRESSIVE -> """
### AGGRESSIVE MODE PROTOCOL
Maximum speed within authorized sandbox constraints:

**Reduced Delays:**
- Cut all delays to 50% of normal
- Accept higher failure rate for speed
- Skip cleanup steps entirely

**Direct Execution:**
- Use most direct command paths
- Keep confirmation dialogs and approvals intact
- Parallel execution if feasible

**Note:** Higher failure risk if delays are too short.
"""
        }
    }

    // ============================================================
    // EVIL PORTAL 3-STEP PROMPTS
    // ============================================================

    object EvilPortal {

        /**
         * STEP 1: GENERATE from screenshot
         */
        fun step1GenerateFromScreenshot(
            additionalInstructions: String,
            portalType: PortalType
        ): String = """
You are DAI-PORTAL, an elite web developer specializing in pixel-perfect captive portal recreation for authorized security testing with Flipper Zero's Evil Portal.

## YOUR MISSION
Analyze the provided screenshot and recreate it as a credential-capturing captive portal page.

## PORTAL TYPE CONTEXT: ${portalType.displayName}

${getPortalTypeGuidance(portalType)}

${if (additionalInstructions.isNotEmpty()) "## ADDITIONAL INSTRUCTIONS\n$additionalInstructions\n" else ""}

## TECHNICAL REQUIREMENTS (NON-NEGOTIABLE)

### Flipper Zero Constraints
1. **Single HTML file** - ALL CSS must be embedded in <style> tags
2. **No JavaScript** - Evil Portal doesn't support it
3. **No external resources** - No CDNs, external fonts, images
4. **Maximum 10KB** - Flipper memory constraint
5. **Form action="/capture"** - REQUIRED for credential capture
6. **Method="POST"** - REQUIRED

### Required Form Structure
```html
<form action="/capture" method="POST">
    <input type="email" name="email" placeholder="Email" required>
    <!-- OR -->
    <input type="text" name="username" placeholder="Username" required>
    <input type="password" name="password" placeholder="Password" required>
    <button type="submit">Sign In</button>
</form>
```

### Visual Recreation Rules
1. Match colors as closely as possible from the screenshot
2. Recreate layout and spacing accurately
3. Use system fonts that approximate the original:
   - `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`
4. Recreate logos using CSS (gradients, shapes) or simple inline SVG
5. Maintain responsive design for mobile devices

### CSS Best Practices for Size
```css
/* Use shorthand */
margin: 10px 20px;  /* not margin-top, margin-right, etc. */
padding: 5px 10px;
border: 1px solid #ccc;

/* Combine selectors */
h1, h2, h3 { font-weight: bold; }

/* Minimize colors - use short hex */
color: #333;  /* not #333333 */
```

## OUTPUT

Generate ONLY the complete HTML file.
- Start with `<!DOCTYPE html>`
- End with `</html>`
- No markdown code blocks
- No explanations
- No comments except essential ones

Generate the HTML now:
""".trimIndent()

        /**
         * STEP 1: GENERATE from description
         */
        fun step1GenerateFromDescription(
            description: String,
            portalType: PortalType,
            brandColors: BrandColors?
        ): String = """
You are DAI-PORTAL, an elite web developer creating convincing captive portal pages for authorized security testing with Flipper Zero's Evil Portal.

## YOUR MISSION
Create a credential-capturing captive portal based on this description:

**DESCRIPTION:** $description

**PORTAL TYPE:** ${portalType.displayName}

${brandColors?.let { """
**BRAND COLORS:**
- Primary: ${it.primary}
${it.secondary?.let { s -> "- Secondary: $s" } ?: ""}
${it.background?.let { b -> "- Background: $b" } ?: ""}
""" } ?: ""}

## PORTAL TYPE GUIDANCE

${getPortalTypeGuidance(portalType)}

## TECHNICAL REQUIREMENTS (NON-NEGOTIABLE)

### Flipper Zero Constraints
1. **Single HTML file** - ALL CSS embedded
2. **No JavaScript** - Not supported
3. **No external resources** - Everything inline
4. **Maximum 10KB** - Strict limit
5. **Form action="/capture" method="POST"** - Required

### Required Input Names
```html
<input type="email" name="email">      <!-- Training email -->
<input type="text" name="username">    <!-- For username capture -->
<input type="password" name="password"> <!-- Always required -->
```

### Professional Design Checklist
- [ ] Clean, trustworthy appearance
- [ ] Proper visual hierarchy
- [ ] Clear call-to-action button
- [ ] Appropriate spacing and padding
- [ ] Mobile-responsive viewport meta tag
- [ ] Form validation states (`:invalid` pseudo-class)
- [ ] Subtle hover states for interactivity

### Template Structure
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>[Appropriate Title]</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            background: [appropriate];
        }
        /* More styles... */
    </style>
</head>
<body>
    <div class="container">
        <!-- Logo/branding area -->
        <form action="/capture" method="POST">
            <!-- Inputs -->
            <button type="submit">Sign In</button>
        </form>
    </div>
</body>
</html>
```

## OUTPUT

Generate ONLY the complete HTML file. No markdown, no explanations.
Start with `<!DOCTYPE html>`:
""".trimIndent()

        /**
         * STEP 2: VALIDATE - Check HTML validity and optimize
         */
        fun step2Validate(
            rawHtml: String,
            portalType: PortalType
        ): String = """
You are DAI-VALIDATOR, an HTML quality assurance expert for Evil Portal pages. Your job is to validate and optimize captive portal HTML for Flipper Zero deployment.

## HTML TO VALIDATE
```html
$rawHtml
```

## PORTAL TYPE: ${portalType.displayName}

## VALIDATION CHECKLIST

### 1. CRITICAL REQUIREMENTS (Must Pass)
- [ ] Form action is exactly `/capture`
- [ ] Form method is `POST`
- [ ] Has `name="email"` or `name="username"` input
- [ ] Has `name="password"` input
- [ ] No JavaScript (script tags, onclick, event handlers)
- [ ] No external resources (http://, https://, //)

### 2. SIZE ANALYSIS
- Calculate total byte size
- If over 10KB, MUST optimize:
  - Remove unnecessary whitespace
  - Shorten CSS selectors
  - Combine redundant rules
  - Use short hex colors (#fff not #ffffff)
  - Remove comments

### 3. HTML VALIDITY
- [ ] Proper DOCTYPE
- [ ] <html>, <head>, <body> structure
- [ ] Viewport meta tag for mobile
- [ ] All tags properly closed
- [ ] No deprecated attributes

### 4. CSS REVIEW
- [ ] All CSS is inline (in <style> tags)
- [ ] No @import statements
- [ ] Efficient selectors
- [ ] Proper mobile responsiveness

### 5. VISUAL/UX CHECK
- [ ] Form is visible and usable
- [ ] Submit button is accessible
- [ ] Text is readable (contrast)
- [ ] Looks professional/trustworthy

## OUTPUT FORMAT

Respond with valid JSON:
```json
{
    "isValid": true/false,
    "html": "THE CORRECTED/OPTIMIZED HTML HERE (minified if was over 10KB)",
    "errors": ["Critical issues that must be fixed"],
    "warnings": ["Non-critical issues"],
    "optimizations": ["Size optimizations made"],
    "sizeBytes": 1234,
    "suggestedActions": [
        {
            "type": "save",
            "label": "Save to Flipper",
            "value": "/ext/apps_data/evil_portal/portals/portal_name/index.html",
            "description": "Deploy to Evil Portal"
        },
        {
            "type": "search",
            "label": "Find official branding",
            "value": "${portalType.name.lowercase()} login page css",
            "description": "Research authentic styling"
        }
    ]
}
```

If the HTML is valid and under 10KB, return it unchanged in the "html" field.
If optimizations were needed, return the optimized version.

Analyze and respond with JSON only:
""".trimIndent()

        /**
         * STEP 3: EXECUTE - Determine deployment actions
         */
        fun step3Execute(
            validatedHtml: String,
            portalName: String,
            context: String = ""
        ): String = """
You are DAI-EXECUTOR, determining optimal deployment actions for an Evil Portal page.

## VALIDATED PORTAL
Name: $portalName
Size: ${validatedHtml.toByteArray().size} bytes

${if (context.isNotEmpty()) "## CONTEXT\n$context\n" else ""}

## AVAILABLE ACTIONS

### 1. save_to_flipper
Save portal to Flipper's Evil Portal directory:
- Creates: /ext/apps_data/evil_portal/portals/$portalName/
- Writes: index.html

### 2. web_search
Search for related resources:
- Brand assets, color codes
- Similar portal examples
- CSS techniques

### 3. execute_command
Run Flipper commands:
- `storage mkdir /ext/apps_data/evil_portal/portals/$portalName`
- `storage stat /ext/apps_data/evil_portal/`

## DETERMINE ACTIONS

What actions should be offered?

Respond with JSON:
```json
{
    "primaryAction": {
        "type": "save",
        "label": "Deploy Portal",
        "value": "/ext/apps_data/evil_portal/portals/$portalName/index.html"
    },
    "secondaryActions": [
        {
            "type": "search",
            "label": "Find brand colors",
            "value": "search query"
        }
    ]
}
```
""".trimIndent()

        private fun getPortalTypeGuidance(type: PortalType): String = when (type) {
            PortalType.GENERIC -> """
### GENERIC PORTAL
- Clean, professional design
- Neutral colors (white, gray, blue)
- Simple "Connect to WiFi" or "Sign In" messaging
- Works for any scenario
"""
            PortalType.CORPORATE_SSO -> """
### CORPORATE SSO STYLE
- Enterprise look and feel
- "Sign in with your organization account"
- Clean white background
- Subtle corporate blue accents
- Professional, trustworthy appearance
- May include "Forgot password?" and "Sign in options" links
"""
            PortalType.MICROSOFT_365 -> """
### MICROSOFT 365 STYLE
- White background
- Microsoft blue (#0078d4) accents
- Segoe UI font family
- Simple centered card layout
- "Sign in" heading
- Email first, then password (two-step pattern)
- "Can't access your account?" link
- Microsoft logo placeholder (simple square grid or text)
"""
            PortalType.GOOGLE_WORKSPACE -> """
### GOOGLE WORKSPACE STYLE
- Clean white background
- Google blue (#4285f4), red (#ea4335), yellow (#fbbc05), green (#34a853)
- "G" logo or "Google" text
- "Sign in" with subtitle "to continue to [service]"
- Email input with "Email or phone" placeholder
- "Forgot email?" link
- "Create account" link at bottom
- Blue "Next" button
"""
            PortalType.SOCIAL_MEDIA -> """
### SOCIAL MEDIA STYLE
- Platform-appropriate colors:
  - Facebook: #1877f2
  - Instagram: gradient (#405DE6 → #E1306C)
  - Twitter/X: #1da1f2 or black
  - LinkedIn: #0077b5
- Centered login card
- "Log in" heading
- Username/email and password fields
- "Forgot password?" link
- Platform logo/name at top
"""
            PortalType.HOTEL_WIFI -> """
### HOTEL/HOSPITALITY STYLE
- Warm, welcoming colors (gold, burgundy, cream)
- "Welcome, Guest" heading
- Room number + Last name fields (instead of email)
- "Connect" button
- Hotel policies/terms text at bottom
- Elegant, hospitality-focused design
"""
            PortalType.COFFEE_SHOP -> """
### COFFEE SHOP STYLE
- Casual, friendly design
- Warm colors (browns, oranges, greens)
- "Free WiFi" prominent messaging
- Just email field (simpler training input)
- "Accept terms to connect" checkbox
- "Connect" button
- Brand-appropriate casual feel
"""
            PortalType.AIRPORT -> """
### AIRPORT/PUBLIC WIFI STYLE
- Clean, institutional design
- Neutral colors (white, gray, blue)
- "Airport Free WiFi" or "Public Network"
- Terms of service acceptance
- Session time limit notice
- Large "Connect" button
- May show flight info integration
"""
            PortalType.BANKING -> """
### BANKING PORTAL STYLE
- Highly professional appearance
- Conservative colors (navy, green, white)
- Strong security messaging
- Bank logo placeholder
- "Online Banking Login" heading
- Username/Customer ID + Password
- "Forgot password?" and "Enroll" links
- Security notice at bottom
- SSL/security badge (CSS recreation)
"""
        }
    }
}

// Extension for display names
val BadUsbPlatform.displayName: String
    get() = when (this) {
        BadUsbPlatform.WINDOWS -> "Windows"
        BadUsbPlatform.MACOS -> "macOS"
        BadUsbPlatform.LINUX -> "Linux"
        BadUsbPlatform.CROSS_PLATFORM -> "Cross-Platform"
    }

val PortalType.displayName: String
    get() = name.replace("_", " ").lowercase()
        .replaceFirstChar { it.uppercase() }
