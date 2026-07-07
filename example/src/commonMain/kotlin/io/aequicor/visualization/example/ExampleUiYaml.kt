package io.aequicor.visualization.example

const val ExampleUiYaml: String = """version: 1
title: External Project Review
description: Example standalone .mv.yaml document consumed by a separate module.
theme:
  name: Lazurite
  primary: "#1F5FA8"
  accent: "#2BB8A8"
  surface: "#F6FAFF"
  mood: precise, calm, product-focused
screens:
  - id: intake
    title: Agent Intake
    description: Reviewer inspects incoming AI-authored UI patches.
    layout:
      type: column
      padding: lg
      gap: md
    children:
      - id: intake-topbar
        type: topBar
        props:
          title: Review Queue
          text: Agent-authored interface changes
      - id: intake-empty
        type: emptyState
        props:
          title: No blocked reviews
          body: Ready changes will stay available in the activity timeline.
      - id: intake-action
        type: button
        style:
          variant: primary
        props:
          text: Open first review
        action:
          type: navigate
          target: review
  - id: review
    title: Review Detail
    description: Reviewer checks component state, table data, and generated prompt context.
    layout:
      type: column
      padding: lg
      gap: md
    children:
      - id: review-tabs
        type: tabs
        props:
          title: Workspace
          items:
            - Preview
            - Comments
            - Prompt
      - id: review-table
        type: dataTable
        props:
          title: Patch contents
          headers:
            - Node
            - Change
            - Status
          rows:
            - intake-action | Copy polish | Needs review
            - intake-empty | Empty copy | Ready
      - id: review-prompt
        type: card
        style:
          tone: info
        props:
          title: Generated prompt
          body: Tighten the primary action copy while preserving reviewer confidence.
scenarios:
  - id: review-first-item
    title: Review first item
    summary: Open a queued review and generate a focused prompt.
    steps:
      - id: open-first
        screenId: intake
        nodeId: intake-action
        action: Open the first review.
        expectation: The preview moves to the review detail screen.
      - id: inspect-prompt
        screenId: review
        nodeId: review-prompt
        action: Inspect the generated prompt.
        expectation: The inspector can generate a deterministic design patch.
comments:
  - id: example-comment
    targetId: review-prompt
    author: reviewer
    body: The generated prompt should name the affected node and expected copy direction.
"""
