# Пример: сценарий контроля миссии

Этот Markdown-файл можно вставить в левую панель приложения Mission Visualization.
Он содержит один строгий fenced-блок `mission-visualization`, который описывает
экраны, компоненты, сценарии и замечания.

```mission-visualization
version: 1
title: Mission Control Onboarding
description: Visualize the onboarding path for operators reviewing agent-authored screens.
theme:
  name: Lazurite
  primary: "#1F5FA8"
  accent: "#2BB8A8"
  surface: "#F6FAFF"
  mood: precise, calm, product-focused
screens:
  - id: dashboard
    title: Mission Dashboard
    description: The operator starts with scenario status, pending design notes, and quick actions.
    components:
      - id: dashboard-topbar
        type: topBar
        title: Mission Visualization
        text: Review AI-authored screens without Figma
      - id: dashboard-summary
        type: section
        title: Active Scenario
        description: High-level mission state and next action.
        children:
          - id: readiness-card
            type: card
            title: Launch readiness
            text: 78% mapped
            description: Shows how much of the scenario has a validated screen state.
          - id: review-button
            type: button
            text: Review scenario
            variant: primary
      - id: agent-notes
        type: list
        title: Agent notes
        items:
          - Clarify empty state copy
          - Mark reusable metric card
          - Generate prompt for primary action polish
      - id: feedback-form
        type: form
        title: Design feedback
        children:
          - id: feedback-input
            type: input
            title: Comment
            placeholder: Leave a focused design note
          - id: feedback-submit
            type: button
            text: Attach comment
            variant: secondary
  - id: prompt-review
    title: Prompt Review
    description: The operator checks generated prompts before sending edits back to an agent.
    components:
      - id: prompt-tabs
        type: tabs
        title: Prompt workspace
        items:
          - Design patch
          - Component notes
          - Scenario context
      - id: prompt-card
        type: card
        title: Generated design prompt
        text: Make the primary action clearer while preserving the lazurite tone.
      - id: export-action
        type: button
        text: Export Markdown
        variant: primary
scenarios:
  - id: review-flow
    title: Review a generated screen
    summary: Operator selects a component, adds comments, and generates a deterministic prompt.
    steps:
      - id: open-dashboard
        screenId: dashboard
        componentId: dashboard-summary
        action: Open the dashboard and inspect the active scenario summary.
        expectation: The visual canvas highlights the relevant screen hierarchy.
      - id: comment-action
        screenId: dashboard
        componentId: review-button
        action: Select the primary action and add a design comment.
        expectation: The inspector stores the note against the component id.
      - id: generate-prompt
        screenId: prompt-review
        componentId: prompt-card
        action: Generate a prompt for the selected design target.
        expectation: Prompt text includes theme, component, comments, and scenario context.
comments:
  - id: comment-dashboard-screen-seed
    targetId: dashboard
    author: reviewer
    body: The dashboard should make the next scenario step obvious before the operator scans details.
  - id: comment-review-button-seed
    targetId: review-button
    author: agent
    body: The primary CTA should feel decisive without becoming visually heavy.
  - id: comment-prompt-card-seed
    targetId: prompt-card
    author: designer
    body: Prompt review needs a clearer separation between generated text and editable notes.
```
