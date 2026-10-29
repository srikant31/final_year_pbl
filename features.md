# Feature brainstorming

Ideas for future additions to Concrete Vision. These features are **not implemented yet**. This is a brainstorming page, not an inventory of current functionality or a commitment to build every idea.

Priority reflects the current student-project scope. “Not important” means optional or deferred. Features should build around the existing analyser without changing its trained model, weights, classes or prediction rules.

## important

| Proposed feature | What the user could do | Initial effort |
|---|---|---|
| **Live-camera inspection mode** | Point the phone at concrete and receive periodically refreshed predictions alongside the preview. Pause on a frame to inspect it. | High |
| **Freeze frame and analyse** | In camera mode, freeze a clear frame, review it and run a deliberate inspection rather than relying only on changing live results. | Medium, after live preview |
| **Share the current result** | Send the photograph and a readable summary through Android’s share sheet to a teacher, colleague or another app. Sharing happens only when requested. | Small to medium |
| **Download a PDF report** | Produce a document containing the photo, result, probabilities, original recommendation, analysis date and model version for a project submission. | Medium |
| **Add inspection notes** | Attach an observation such as “water leakage nearby” or “inspect after repair” to a report before sharing/exporting it. | Small |
| **Identify the inspected element** | Enter a label such as “Floor 2, Column C4” so an exported report identifies the physical member. | Small |
| **Photo-taking guide** | Open a visual guide explaining useful lighting, distance, framing and common mistakes before taking an inspection photo. | Small |
| **Photo-quality warnings** | Receive advisory warnings for excessive blur or darkness, with a Retake option. These checks would not change the model’s prediction or declare concrete safe. | Medium |
| **Compare before and after repair** | Place two photographs side by side, add observations and export the comparison. Do not automatically claim measured damage reduction from confidence changes. | Medium |
| **Batch photo analysis** | Select several photographs, process them sequentially and view a result for each, with progress and cancellation. | Medium |
| **Inspection checklist** | Complete a short checklist covering the element, visible condition, surrounding moisture and follow-up needs alongside the model report. | Small to medium |
| **Mark observations on a photo** | Draw an arrow, circle or text label to indicate the area of concern. Keep the original photo for inference; annotations belong to a separate display/export copy. | Medium |

### Easiest first additions

- **Inspection notes and element labels:** small forms with immediate value in reports.
- **Text-result sharing:** a useful first export feature before designing a PDF.
- **Photo-taking guide:** a simple visual help screen.
- **Inspection checklist:** a predefined form that can be included in shared reports.

Live-camera inference and PDF generation need more integration work. Live preview FPS and prediction-update rate should be treated separately; do not promise real-time accuracy or a fixed frame rate before testing.

## not important

### Site-based inspection gallery

The previously discussed Google Photos-style organization remains a deferred idea:

**Job site → Visit → Inspection photos and saved reports**

Possible additions within this feature:

- Create named job sites and dated visits.
- Browse photo thumbnails grouped by site and visit.
- Save a photograph together with its original analysis, element label and notes.
- Read the original capture date and GPS from EXIF metadata when available.
- Keep capture time/location separate from inspection time, site location and current phone GPS.
- Allow clearly labelled manual entries when metadata is missing.
- Search/filter by site, visit date, element or predicted defect.
- Open saved reports offline without rerunning the model.
- Export and restore complete site backups, including photos and record relationships.

Missing photo metadata should remain unknown rather than being invented. A locally saved gallery would need backup/restore before being relied upon as the only site record.

### Other optional ideas

| Proposed feature | What it could add | Why defer it |
|---|---|---|
| **Site map** | View manually assigned or photo-derived inspection coordinates on a map. | Requires location handling and decisions about offline maps. |
| **QR codes for structural elements** | Scan a code attached to a column or beam to open that element’s inspection records. | Most useful after sites and saved records exist. |
| **Follow-up reminders** | Set a user-chosen date to revisit an inspection or check a repair. | Depends on persistent records and notification handling. |
| **Inspection status tags** | Mark records as Needs review, Repair planned or Rechecked. These are user workflow tags, not model safety conclusions. | Fits the later gallery/workflow system. |
| **Voice notes** | Record a spoken observation while on site and attach it to an inspection. | Adds microphone permissions, storage and export complexity. |
| **Multiple interface languages** | Offer translated navigation and help content. | Requires translation and review; technical recommendations must not be mistranslated. |
| **Cloud backup and device sync** | Access saved inspections on another device and recover them after phone loss. | Adds accounts, hosting, privacy decisions and ongoing costs. |
| **Team review** | Let a supervisor comment on a shared inspection and track review status. | Requires collaboration infrastructure and explicit sharing controls. |
| **Site summary dashboard** | Show counts of inspections, predicted defect categories and follow-ups per site. | Needs saved records; confidence-based severity must not become a claimed structural-risk score. |
| **Custom report branding** | Add a college/company name, project title or logo to exported reports. | Useful after basic export is working. |
| **Measurement reference in photographs** | Let the user record a ruler/reference scale and manual measurements beside a photo. | Actual automatic crack-width measurement is a separate calibrated computer-vision project, not a capability of the existing classifier. |

### Ideas that require separate model work

Healthy/no-defect classification, unknown-image rejection, automatic defect outlines, multiple-defect detection and automatic crack-width estimation are **not simple interface additions**. They would need additional validated models, algorithms or training and are outside the current “keep the trained model unchanged” scope. They are not promised by this brainstorm.
