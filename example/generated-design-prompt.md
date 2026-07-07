# Пример результата Prompt

Такой текст появляется в инспекторе после нажатия единой кнопки `Prompt`.
Он собирает тему, карту экранов, сценарий и все замечания по всем target id.

```text
You are updating the complete mission-visualization design.
Design tone: Lazurite (precise, calm, product-focused); primary #1F5FA8, accent #2BB8A8.
Mission: Mission Control Onboarding.
Mission intent: Visualize the onboarding path for operators reviewing agent-authored screens.
Screens and components:
- Mission Dashboard (dashboard): topBar:dashboard-topbar, section:dashboard-summary, card:readiness-card, button:review-button, list:agent-notes, form:feedback-form, input:feedback-input, button:feedback-submit
- Prompt Review (prompt-review): tabs:prompt-tabs, card:prompt-card, button:export-action
Scenario context:
- [dashboard-summary] Open the dashboard and inspect the active scenario summary. -> The visual canvas highlights the relevant screen hierarchy.
- [review-button] Select the primary action and add a design comment. -> The inspector stores the note against the component id.
- [prompt-card] Generate a prompt for the selected design target. -> Prompt text includes theme, component, comments, and scenario context.
All comments to address:
- Mission Dashboard (dashboard): The dashboard should make the next scenario step obvious before the operator scans details.
- Mission Dashboard / button 'Review scenario' (review-button): The primary CTA should feel decisive without becoming visually heavy.
- Prompt Review / card 'Generated design prompt' (prompt-card): Prompt review needs a clearer separation between generated text and editable notes.
Return one cohesive design patch covering navigation, component states, layout, color/token changes, and copy adjustments.
```
