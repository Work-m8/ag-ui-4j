---
layout: default
title: "Welcome to Project Documentation"
---

# Welcome to Our Documentation

This site contains comprehensive documentation for our project, including user guides, tutorials, and complete API reference.

## Quick Navigation

<div class="grid">
  <div class="card">
    <h3><a href="/guides/">📖 User Guides</a></h3>
    <p>Step-by-step guides to help you get started and configure the project.</p>
  </div>

  <div class="card">
    <h3><a href="/tutorials/">🎯 Tutorials</a></h3>
    <p>Hands-on tutorials covering basic usage and advanced features.</p>
  </div>

  <div class="card">
    <h3><a href="/docs/api/">⚙️ API Reference</a></h3>
    <p>Complete API documentation generated from source code.</p>
  </div>

  <div class="card">
    <h3><a href="/blog/">📝 Blog</a></h3>
    <p>Latest updates, release notes, and project news.</p>
  </div>
</div>

## Getting Started

1. **Installation**: Follow our [getting started guide](/guides/getting-started/)
2. **Configuration**: Learn how to [configure the project](/guides/configuration/)
3. **First Steps**: Try our [basic usage tutorial](/tutorials/basic-usage/)

## Latest Updates

{% for post in site.posts limit:3 %}
- [{{ post.title }}]({{ post.url }}) - {{ post.date | date: "%B %d, %Y" }}
{% endfor %}

---

*Documentation built with Jekyll and served via Docker. Last updated: {{ site.time | date: "%Y-%m-%d %H:%M" }}*