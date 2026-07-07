package io.aequicor.visualization.ui_engine.mv_yaml_source

const val SampleUiYaml: String = """version: 1
title: Mission Control Onboarding
description: Visualize the onboarding path for operators reviewing agent-authored screens.
metadata:
  authoringFormat: mv.yaml
  audience: reviewer
theme:
  name: Lazurite
  primary: "#1F5FA8"
  accent: "#2BB8A8"
  surface: "#F6FAFF"
  mood: precise, calm, product-focused
  tokens:
    success: "#1E8E5A"
    warning: "#C77800"
screens:
  - id: dashboard
    title: Mission Dashboard
    description: The operator starts with scenario status, pending design notes, and quick actions.
    layout:
      type: column
      padding: lg
      gap: md
    children:
      - id: dashboard-topbar
        type: topBar
        props:
          title: Mission Visualization
          text: Review AI-authored screens without Figma
      - id: dashboard-shell
        type: sidebar
        layout:
          type: row
          gap: md
        props:
          title: Scenario workspace
          items:
            - Overview
            - Components
            - Prompts
        children:
          - id: dashboard-summary
            type: section
            layout:
              type: column
              gap: md
            props:
              title: Active Scenario
              body: High-level mission state and next action.
            children:
              - id: readiness-card
                type: card
                style:
                  tone: info
                props:
                  title: Launch readiness
                  body: 78% mapped
                  text: Shows how much of the scenario has a validated screen state.
              - id: readiness-badge
                type: badge
                style:
                  tone: success
                props:
                  text: Ready for review
              - id: review-button
                type: button
                style:
                  variant: primary
                props:
                  text: Review scenario
                action:
                  type: navigate
                  target: prompt-review
          - id: agent-notes
            type: list
            props:
              title: Agent notes
              items:
                - Clarify empty state copy
                - Mark reusable metric card
                - Generate prompt for primary action polish
      - id: feedback-form
        type: form
        layout:
          type: column
          gap: sm
        props:
          title: Design feedback
        children:
          - id: feedback-input
            type: input
            props:
              title: Comment
              placeholder: Leave a focused design note
          - id: feedback-submit
            type: button
            style:
              variant: secondary
            props:
              text: Attach comment
            action:
              type: submit
  - id: prompt-review
    title: Prompt Review
    description: The operator checks generated prompts before sending edits back to an agent.
    layout:
      type: column
      padding: lg
      gap: md
    children:
      - id: prompt-tabs
        type: tabs
        props:
          title: Prompt workspace
          items:
            - Design patch
            - Component notes
            - Scenario context
      - id: prompt-card
        type: card
        style:
          tone: primary
        props:
          title: Generated design prompt
          body: Make the primary action clearer while preserving the lazurite tone.
      - id: prompt-history
        type: timeline
        props:
          title: Review activity
          items:
            - Agent generated first pass
            - Reviewer selected primary CTA
            - Prompt patch queued
      - id: component-table
        type: dataTable
        props:
          title: Component coverage
          headers:
            - Component
            - Status
            - Owner
          rows:
            - review-button | Needs polish | agent
            - prompt-card | Ready | designer
            - feedback-input | Draft | reviewer
      - id: empty-state
        type: emptyState
        props:
          title: No blocked prompts
          body: Generated prompts that need attention will appear here.
      - id: export-menu
        type: menu
        props:
          title: Export
          items:
            - Copy YAML
            - Generate prompt
            - Download preview
      - id: confirmation-dialog
        type: dialog
        props:
          title: Send design patch?
          body: Review generated prompt content before handing it to another agent.
        children:
          - id: confirm-export
            type: button
            style:
              variant: primary
            props:
              text: Confirm export
scenarios:
  - id: review-flow
    title: Review a generated screen
    summary: Operator selects a component, adds comments, and generates a deterministic prompt.
    steps:
      - id: open-dashboard
        screenId: dashboard
        nodeId: dashboard-summary
        action: Open the dashboard and inspect the active scenario summary.
        expectation: The visual canvas highlights the relevant screen hierarchy.
      - id: comment-action
        screenId: dashboard
        nodeId: review-button
        action: Select the primary action and add a design comment.
        expectation: The inspector stores the note against the node id.
      - id: generate-prompt
        screenId: prompt-review
        nodeId: prompt-card
        action: Generate a prompt for the selected design target.
        expectation: Prompt text includes theme, node props, comments, and scenario context.
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
"""
