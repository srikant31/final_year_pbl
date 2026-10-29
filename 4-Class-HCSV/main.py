import os
import tkinter as tk
from tkinter import filedialog, ttk
from PIL import Image, ImageTk
import numpy as np

import torch
import torch.nn as nn
from torchvision import models, transforms as tv_transforms

# ==================== PATHS ====================

IMG_SIZE   = 224
BASE_DIR   = os.path.dirname(os.path.abspath(__file__))
MODELS_DIR = os.path.join(BASE_DIR, "model")

MODEL_PATH = os.path.join(MODELS_DIR, "best_model_4class_hcsv_20251103_1716.pth")
CLASSES    = ["honeycombing", "crack", "spalling", "void"]
MODEL_NAME = "Flowx"
LOGO_PATH  = os.path.join(BASE_DIR, "..", "logo.jpeg")

# ==================== THEME ====================

BG_ROOT   = "#0d1b2a"
BG_CARD   = "#1b2838"
BG_HEADER = "#0a1628"
BG_INPUT  = "#111d2e"
ACCENT    = "#00aaff"
ACCENT2   = "#0077cc"
TEXT_PRI  = "#e8edf2"
TEXT_SEC  = "#7a90a8"
SEV_HIGH  = "#ef4444"
SEV_MED   = "#f59e0b"
SEV_LOW   = "#10b981"
BTN_UP    = "#1d6fa5"
BTN_RUN   = "#166534"
BTN_CLR   = "#7f1d1d"

# ==================== PREPROCESSING ====================

pytorch_transform = tv_transforms.Compose([
    tv_transforms.Resize((IMG_SIZE, IMG_SIZE)),
    tv_transforms.ToTensor(),
    tv_transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])

# ==================== MODEL ====================

def build_resnet50(num_classes):
    m = models.resnet50(weights=None)
    m.fc = nn.Sequential(nn.Dropout(0.5), nn.Linear(m.fc.in_features, num_classes))
    return m

def load_model():
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    m = build_resnet50(len(CLASSES))
    ckpt = torch.load(MODEL_PATH, map_location=device, weights_only=False)
    sd = ckpt.get("model_state_dict", ckpt) if isinstance(ckpt, dict) else ckpt
    m.load_state_dict(sd)
    return m.to(device).eval(), device

# ==================== RECOMMENDATION ====================

def get_severity(conf):
    if conf > 0.85: return "HIGH"
    if conf > 0.60: return "MEDIUM"
    return "LOW"

def get_action(defect, severity):
    d, s = defect.lower(), severity.lower()
    if "spalling" in d:
        if "high" in s:
            return (
                "URGENT — Structural Integrity at Risk.\n"
                "  1. Cordon off the affected zone immediately per IS 456 / ACI 318 safety protocols.\n"
                "  2. Conduct half-cell potential & carbonation depth tests to assess rebar corrosion extent.\n"
                "  3. Break out all delaminated concrete to sound substrate (minimum 25 mm beyond spall edge).\n"
                "  4. Clean exposed rebar with wire brushing; apply epoxy-based anti-corrosion primer.\n"
                "  5. Reinstate using polymer-modified cementitious repair mortar (≥ M40 equivalent).\n"
                "  6. Apply a penetrating silane-siloxane sealer to the repaired surface.\n"
                "  7. Commission a licensed structural engineer to evaluate load capacity and"
                " specify any additional strengthening (e.g., CFRP wrapping or section enlargement).\n"
                "  Ref: IS 9537 / ICRI Guideline No. 310.2R"
            )
        if "medium" in s:
            return (
                "Moderate Spalling — Remediation Required Within 30 Days.\n"
                "  1. Remove loose and unsound concrete using chipping hammer or hydro-jetting.\n"
                "  2. Check rebar cover depth; restore to IS 456 minimum (40 mm for severe exposure).\n"
                "  3. Apply polymer-modified repair mortar (class R3 per EN 1504-3) in layers ≤ 10 mm.\n"
                "  4. Cure for minimum 7 days under wet burlap or curing compound.\n"
                "  5. Apply elastomeric coating or anti-carbonation coating to prevent recurrence.\n"
                "  Ref: IS 13935 / EN 1504 Part 3"
            )
        return (
            "Minor Surface Spalling — Monitor & Seal.\n"
            "  1. Clean the affected area with a wire brush to remove dust and loose material.\n"
            "  2. Fill with cement-sand slurry or acrylic-modified patching compound.\n"
            "  3. Schedule quarterly visual inspection for progression.\n"
            "  4. Ensure adequate drainage to prevent water ponding near the surface.\n"
            "  Ref: IS 2212 / ACI 546R"
        )
    if "crack" in d:
        if "high" in s:
            return (
                "CRITICAL — Structural Crack Detected.\n"
                "  1. Immediately restrict access and notify the structural engineer of record.\n"
                "  2. Install tell-tales or crack-width gauges to monitor crack propagation.\n"
                "  3. Conduct ground-penetrating radar (GPR) or ultrasonic pulse velocity (UPV)"
                " test to map crack depth and extent.\n"
                "  4. For active cracks (width > 0.3 mm, IS 456): perform epoxy pressure injection"
                " using low-viscosity resin (≤ 200 cP) at ports spaced 150–300 mm.\n"
                "  5. For flexural or shear cracks: evaluate structural adequacy per IS 456 Cl. 22;"
                " design and install CFRP strips or steel plate bonding as required.\n"
                "  6. Address root cause: overloading, differential settlement, shrinkage.\n"
                "  Ref: IS 456 / ACI 224R / ICRI 310.3R"
            )
        return (
            "Non-Structural / Hairline Crack — Seal & Observe.\n"
            "  1. Map crack pattern to classify (shrinkage, thermal, or settlement origin).\n"
            "  2. For cracks < 0.2 mm width: apply flexible polyurethane or epoxy crack sealer.\n"
            "  3. For cracks 0.2–0.3 mm: route and seal using V-groove cutting and elastomeric sealant.\n"
            "  4. Apply waterproof crystalline coating to prevent water ingress and rebar corrosion.\n"
            "  5. Schedule 6-monthly monitoring; escalate if crack width increases by > 0.05 mm.\n"
            "  Ref: IS 456 Table 20 / ACI 224R"
        )
    if "void" in d:
        return (
            "Voids Detected — Grouting & Structural Review Required.\n"
            "  1. Delineate void extent using GPR survey or endoscopic inspection.\n"
            "  2. Drill injection ports (dia. 12–16 mm) at 200–400 mm spacing around the void zone.\n"
            "  3. Flush voids with water under low pressure to remove dust; allow to dry.\n"
            "  4. Inject non-shrink cementitious or epoxy grout under controlled pressure"
            " until grout returns at adjacent ports.\n"
            "  5. Seal ports after grout sets; retest with UPV to confirm void elimination.\n"
            "  6. If void is sub-structural (e.g., under slab): consult geotechnical engineer"
            " to assess bearing capacity impact.\n"
            "  Ref: IS 9537 / ACI 351.1R / EN 1504-5"
        )
    if "honeycombing" in d:
        if "high" in s:
            return (
                "CRITICAL — Severe Honeycombing Detected.\n"
                "  1. STOP all loading on the affected member immediately.\n"
                "  2. Commission a specialist structural assessment; consider temporary propping.\n"
                "  3. Conduct core drilling and UPV/GPR testing to determine void volume and rebar exposure.\n"
                "  4. If void depth > 50% of member thickness or rebar is exposed over > 300 mm length:"
                " partial or full demolition and re-casting may be required.\n"
                "  5. If repair is feasible: remove all loose aggregate, clean rebar, and fill voids"
                " using non-shrink cementitious grout (≥ M50) via pressure injection or dry-packing.\n"
                "  6. Apply passivation layer on rebars before grouting.\n"
                "  Ref: IS 456 Cl. 13 / ACI 301 / EN 1504-6"
            )
        return (
            "Localised Honeycombing — Repair Required.\n"
            "  1. Mark the full extent of honeycombing using hammer sounding or UPV testing.\n"
            "  2. Cut perimeter to sound concrete with a saw; remove loose material mechanically.\n"
            "  3. Sandblast or water-blast surface to ICRI CSP-5 profile.\n"
            "  4. Apply bonding agent (epoxy or SBR slurry); then fill with non-shrink repair mortar"
                " or micro-concrete (min. compressive strength 40 MPa at 28 days).\n"
            "  5. Cure thoroughly; apply protective coating on completion.\n"
            "  Ref: IS 456 / ACI 546R / ICRI 310.2R"
        )
    return (
        "Manual assessment required by a licensed structural / civil engineer.\n"
        "  Provide full documentation including: crack maps, photographic evidence,"
        " cover depth readings, and load history for proper diagnosis."
    )

# ==================== GUI ====================

class App:
    def __init__(self, root):
        self.root = root
        self.root.title(f"Concrete Vision — {MODEL_NAME}")
        self.root.geometry("1280x820")
        self.root.configure(bg=BG_ROOT)
        self.root.resizable(True, True)

        self.current_image = None
        self.model = None
        self.device = None

        self._setup_styles()
        self._build_header()
        self._build_footer()
        self._build_content()

    def _setup_styles(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Blue.Horizontal.TProgressbar",
                        troughcolor=BG_INPUT, background=ACCENT,
                        bordercolor=BG_CARD, lightcolor=ACCENT, darkcolor=ACCENT2)
        self.progress = ttk.Progressbar(self.root, mode="indeterminate",
                                        style="Blue.Horizontal.TProgressbar")

    def _build_header(self):
        hdr = tk.Frame(self.root, bg=BG_HEADER)
        hdr.pack(fill=tk.X)
        inner = tk.Frame(hdr, bg=BG_HEADER)
        inner.pack(pady=12)

        row = tk.Frame(inner, bg=BG_HEADER)
        row.pack()

        try:
            logo_img = Image.open(LOGO_PATH)
            logo_img.thumbnail((64, 64))
            self._logo_photo = ImageTk.PhotoImage(logo_img)
            tk.Label(row, image=self._logo_photo, bg=BG_HEADER).pack(side=tk.LEFT, padx=(0, 16))
        except Exception:
            pass

        text_col = tk.Frame(row, bg=BG_HEADER)
        text_col.pack(side=tk.LEFT)
        tk.Label(text_col, text=MODEL_NAME,
                 font=("Segoe UI", 22, "bold"), bg=BG_HEADER, fg=TEXT_PRI).pack(anchor="w")
        tk.Label(text_col, text="Concrete Defect Detection  —  4-Class HCSV",
                 font=("Segoe UI", 10), bg=BG_HEADER, fg=ACCENT).pack(anchor="w", pady=(2, 0))
        tk.Label(text_col, text="See Quality. Ensure Safety.",
                 font=("Segoe UI", 9, "italic"), bg=BG_HEADER, fg=TEXT_SEC).pack(anchor="w")

        tk.Frame(inner, bg=ACCENT, height=2).pack(fill=tk.X, pady=(10, 0))

    def _build_footer(self):
        footer = tk.Frame(self.root, bg=BG_HEADER)
        footer.pack(fill=tk.X, side=tk.BOTTOM)
        tk.Frame(footer, bg=BG_INPUT, height=1).pack(fill=tk.X)

        row = tk.Frame(footer, bg=BG_HEADER)
        row.pack(pady=14, padx=24, anchor="w")

        for text, cmd, bg, hover in [
            ("  Upload Image      ", self.upload_image,          BTN_UP,    "#2563eb"),
            ("  Run Analysis      ", self.classify_image,        BTN_RUN,   "#15803d"),
            ("  Confusion Matrix  ", self.show_confusion_matrix, "#4a1d96", "#6d28d9"),
            ("  Clear             ", self.clear,                 BTN_CLR,   "#991b1b"),
        ]:
            btn = tk.Button(row, text=text, command=cmd,
                            font=("Segoe UI", 10, "bold"), bg=bg, fg="white",
                            relief=tk.FLAT, padx=10, pady=8, cursor="hand2",
                            activebackground=hover, activeforeground="white", bd=0)
            btn.pack(side=tk.LEFT, padx=(0, 10))
            btn.bind("<Enter>", lambda e, b=btn, h=hover: b.config(bg=h))
            btn.bind("<Leave>", lambda e, b=btn, n=bg:   b.config(bg=n))

        self.status_lbl = tk.Label(row, text="", font=("Segoe UI", 9),
                                   bg=BG_HEADER, fg=TEXT_SEC)
        self.status_lbl.pack(side=tk.LEFT, padx=16)

    def _build_content(self):
        content = tk.Frame(self.root, bg=BG_ROOT)
        content.pack(fill=tk.BOTH, expand=True, padx=20, pady=16)

        left = tk.Frame(content, bg=BG_CARD)
        left.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 10))
        self._panel_title(left, "IMAGE PREVIEW")
        self.image_lbl = tk.Label(left, bg=BG_CARD, fg=TEXT_SEC,
                                  text="No image loaded\n\nUpload an image to begin analysis",
                                  font=("Segoe UI", 11), justify=tk.CENTER)
        self.image_lbl.pack(fill=tk.BOTH, expand=True, padx=12, pady=12)

        right = tk.Frame(content, bg=BG_CARD)
        right.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)
        self._panel_title(right, "ANALYSIS REPORT")

        txt_frame = tk.Frame(right, bg=BG_CARD)
        txt_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=(6, 10))

        sb = tk.Scrollbar(txt_frame, bg=BG_INPUT, troughcolor=BG_INPUT,
                          activebackground=ACCENT2, relief=tk.FLAT, width=8)
        sb.pack(side=tk.RIGHT, fill=tk.Y)

        self.result_txt = tk.Text(
            txt_frame, font=("Consolas", 11), bg=BG_INPUT, fg=TEXT_PRI,
            relief=tk.FLAT, padx=16, pady=14, wrap=tk.WORD,
            state=tk.DISABLED, yscrollcommand=sb.set,
            selectbackground=ACCENT2, spacing1=2, spacing3=2,
        )
        self.result_txt.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        sb.config(command=self.result_txt.yview)

        self.result_txt.tag_config("heading",  foreground=ACCENT,    font=("Consolas", 11, "bold"))
        self.result_txt.tag_config("high",     foreground=SEV_HIGH,  font=("Consolas", 11, "bold"))
        self.result_txt.tag_config("medium",   foreground=SEV_MED,   font=("Consolas", 11, "bold"))
        self.result_txt.tag_config("low",      foreground=SEV_LOW,   font=("Consolas", 11, "bold"))
        self.result_txt.tag_config("label",    foreground=TEXT_SEC)
        self.result_txt.tag_config("value",    foreground=TEXT_PRI,  font=("Consolas", 11, "bold"))
        self.result_txt.tag_config("action",   foreground="#fbbf24", font=("Consolas", 10))
        self.result_txt.tag_config("muted",    foreground=TEXT_SEC,  font=("Consolas", 10))
        self.result_txt.tag_config("selected", foreground=ACCENT,    font=("Consolas", 11, "bold"))

    def _panel_title(self, parent, text):
        hdr = tk.Frame(parent, bg=ACCENT2)
        hdr.pack(fill=tk.X)
        tk.Label(hdr, text=text, font=("Segoe UI", 8, "bold"),
                 bg=ACCENT2, fg="white", pady=6, padx=12, anchor="w").pack(fill=tk.X)

    def _set_status(self, msg, color=TEXT_SEC):
        self.status_lbl.config(text=msg, fg=color)
        self.root.update_idletasks()

    def _write_rich(self, segments):
        self.result_txt.config(state=tk.NORMAL)
        self.result_txt.delete(1.0, tk.END)
        for text, tag in segments:
            self.result_txt.insert(tk.END, text, tag) if tag else self.result_txt.insert(tk.END, text)
        self.result_txt.config(state=tk.DISABLED)

    def _write(self, text):
        self._write_rich([(text, None)])

    def _bar(self): return "─" * 54

    def _sev_tag(self, sev):
        s = sev.lower()
        if "high" in s:   return "high"
        if "medium" in s: return "medium"
        return "low"

    def _ensure_loaded(self):
        if self.model is None:
            self._set_status("Loading model...", ACCENT)
            self.model, self.device = load_model()
            self._set_status("Ready", SEV_LOW)

    def upload_image(self):
        path = filedialog.askopenfilename(
            filetypes=[("Images", "*.jpg *.jpeg *.png *.bmp")])
        if not path:
            return
        self.current_image = Image.open(path).convert("RGB")
        preview = self.current_image.copy()
        preview.thumbnail((500, 500))
        photo = ImageTk.PhotoImage(preview)
        self.image_lbl.configure(image=photo, text="")
        self.image_lbl.image = photo
        self._write("\n  Image loaded.\n  Click 'Run Analysis' to begin.")
        self._set_status("Image ready", ACCENT)

    def classify_image(self):
        if self.current_image is None:
            self._write("\n  Please upload an image first.")
            return

        self._write("\n  Analysing image, please wait...")
        self._set_status("Running...", ACCENT)
        self.progress.pack(fill=tk.X, padx=20, pady=(0, 4))
        self.progress.start(12)
        self.root.update_idletasks()

        try:
            self._ensure_loaded()
            t = pytorch_transform(self.current_image).unsqueeze(0).to(self.device)
            with torch.no_grad():
                probs = torch.softmax(self.model(t), dim=1)[0].cpu().numpy()
            idx    = int(np.argmax(probs))
            defect = CLASSES[idx]
            conf   = float(probs[idx])
            all_probs = dict(zip(CLASSES, probs.tolist()))

            sev    = get_severity(conf)
            action = get_action(defect, sev)
            stag   = self._sev_tag(sev)

            prob_lines = []
            for c, p in sorted(all_probs.items(), key=lambda x: -x[1]):
                marker = ">" if c == defect else " "
                prob_lines.append((f"   {marker} {c.capitalize():<16} {p*100:5.1f}%\n",
                                   "selected" if c == defect else "muted"))

            seg = [
                (f"\n  {self._bar()}\n", "label"),
                ("  ANALYSIS REPORT\n", "heading"),
                (f"  {self._bar()}\n\n", "label"),
                ("  Model       : ", "label"), (f"{MODEL_NAME}\n\n", "value"),
                ("  Finding     : ", "label"), (f"{defect.upper()}\n", "value"),
                ("  Severity    : ", "label"), (f"{sev}\n", stag),
                ("  Confidence  : ", "label"), (f"{conf*100:.1f}%\n\n", "value"),
                (f"  {self._bar()}\n", "label"),
                ("  CLASS PROBABILITIES\n", "heading"),
                (f"  {self._bar()}\n", "label"),
            ]
            seg += prob_lines
            seg += [
                (f"\n  {self._bar()}\n", "label"),
                ("  ACTION REQUIRED\n", "heading"),
                (f"  {self._bar()}\n", "label"),
                (f"  {action}\n\n", "action"),
                (f"  {self._bar()}\n", "label"),
            ]
            self._write_rich(seg)
            self._set_status("Analysis complete", SEV_LOW)

        except Exception as e:
            self._write(f"\n  Error during analysis:\n  {e}")
            self._set_status("Error", SEV_HIGH)
        finally:
            self.progress.stop()
            self.progress.pack_forget()

    def show_confusion_matrix(self):
        img_path = os.path.join(BASE_DIR, "confusion-matrix", "confusion_matrix.png")
        if not os.path.exists(img_path):
            self._set_status("Confusion matrix image not found.", SEV_HIGH)
            return

        win = tk.Toplevel(self.root)
        win.title("Confusion Matrix — Flowx (4-Class HCSV)")
        win.configure(bg=BG_ROOT)
        win.resizable(True, True)

        img = Image.open(img_path)
        photo = ImageTk.PhotoImage(img)
        lbl = tk.Label(win, image=photo, bg=BG_ROOT)
        lbl.image = photo
        lbl.pack(padx=16, pady=16)

        win.geometry(f"{img.width + 32}x{img.height + 70}")
        tk.Button(win, text="  Close  ", command=win.destroy,
                  font=("Segoe UI", 10, "bold"), bg=BTN_CLR, fg="white",
                  relief=tk.FLAT, padx=10, pady=6, cursor="hand2",
                  activebackground="#991b1b", bd=0).pack(pady=(0, 12))

    def clear(self):
        self.current_image = None
        self.image_lbl.configure(image="",
                                 text="No image loaded\n\nUpload an image to begin analysis")
        self.image_lbl.image = None
        self._write("")
        self._set_status("", TEXT_SEC)


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
