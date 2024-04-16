# FCS Miscellaneous Resources

This repo contains AsciiDoc sources, images, examples and schema files for the CLARIN Federated Content Search (FCS) specification documents found at [clarin.eu](https://www.clarin.eu/content/federated-content-search-clarin-fcs-technical-details). Prior versions of the specification documents with examples and schema files are also backed up in the [`historical/`](historical/) folder. Current builds of the AsciiDoc sources are available [on this page](https://clarin-eric.github.io/fcs-misc/).

Jump to: [[Specification Documents](#specification-documents)]
| [[Tutorial Documents](#tutorial-documents)]
| [[Historical Resources](#historical-resources)]
| [[Webpage with Builds](#webpage-with-builds)]

## Specification Documents

- [CLARIN Federated Content Search - FCS **Core 2.0**: `fcs-core-2.0/index.adoc`](fcs-core-2.0/index.adoc)
- [CLARIN Federated Content Search - FCS **Core 1.0**: `fcs-core-1.0/index.adoc`](fcs-core-1.0/index.adoc)
- [CLARIN Federated Content Search - FCS **Data Views 1.0**: `fcs-dataviews-1.0/index.adoc`](fcs-dataviews-1.0/index.adoc)
- _WIP_ [CLARIN Federated Content Search - FCS **AAI 1.0**: `fcs-aai/index.adoc`](fcs-aai/index.adoc)
- _WIP_ [Text+ **LexFCS 1.0**: `lexfcs/index.adoc`](lexfcs/index.adoc) _(Mirror of [Text+ Gitlab Repo](https://gitlab.gwdg.de/textplus/ag-fcs-documents))_

### Folder Structure

All the specification documents are structured as follows in their sub folders:
- `index.adoc` -- AsciiDoc entrypoint document that bundles and includes single chapters into one
- `attachments/` -- (optional) with schema files or similar
- `examples/` -- (optional) examples (or fragments) that are included in the specification document
- `images/` -- (optional) for images that will be in the specification
- `themes/` -- links to the global [`themes/`](themes/) folder with the `clarin` theme

### AsciiDoc

* global [`themes/`](themes/) folder for CLARIN

### How to build

Please take a look at the [Github Actions workflow definitions in `.github/workflows`](.github/workflows). All the specification documents will be built automatically when their source files change. (_NOTE: changes to theme files may require manually triggering the build._)

You can build the specifications documents yourself with:

```bash
# Set spec you want to build
# Based on folder names, choose one of: fcs-core-1.0, fcs-core-2.0, fcs-aai, fcs-dataviews-1.0
NAME=fcs-core-2.0

# Output will be placed in `docs/`

# Build HTML
asciidoctor -v -D docs -a data-uri --backend=html5 -o ${NAME}.html ${NAME}/index.adoc

# Build PDF
asciidoctor-pdf -v -D docs -o ${NAME}.pdf ${NAME}/index.adoc

# (optional) Copy attachments
cp -R ${NAME}/attachments docs/

# (optional) Copy examples (are already included into the documents)
cp -R ${NAME}/examples docs/
```

You can use the [`asciidoctor/docker-asciidoctor` docker image](https://github.com/asciidoctor/docker-asciidoctor/blob/main/README.adoc) for building the specifications if you don't want to install various dependencies. Note that the build artifacts belong to the `root` user.

```bash
docker run --rm -it -v $(pwd):/documents asciidoctor/docker-asciidoctor
# then run your build commands
```

## Tutorial Documents

* [CLARIN Federated Content Search - FCS **Endpoint Developer's Tutorial**: `fcs-endpoint-dev-tutorial/index.adoc`](fcs-endpoint-dev-tutorial/index.adoc)

  For build instructions, see section [Specification Documents "How to build"](#how-to-build).

* [CLARIN Federated Content Search - FCS **Endpoint Development** Slides: `fcs-endpoint-dev-slides`](fcs-endpoint-dev-slides/index.adoc)

  This is a RevealJS slide deck based on AsciiDoc and needs slightly different build steps. Those are currently only listed in the [GitHub Actions Workflow](.github/workflows/build-fcs-endpoint-dev-slides-adoc.yml). The following are a copy:

  ```bash
  # Output will be placed in `slides/`

  # Setup "dependencies"
  git clone -b 4.1.2 --depth 1 https://github.com/hakimel/reveal.js.git
  mkdir -p slides/reveal.js
  mv reveal.js/dist slides/reveal.js/
  mv reveal.js/plugin slides/reveal.js/
  rm -rf reveal.js/

  git clone -b 10.7.3 --depth 1 https://github.com/highlightjs/highlight.js.git
  mv highlight.js/src/styles/github.css slides/reveal.js/plugin/highlight/
  mv highlight.js/src/styles/idea.css slides/reveal.js/plugin/highlight/
  mv highlight.js/src/styles/magula.css slides/reveal.js/plugin/highlight/
  rm -rf highlight.js/

  # Build Slides
  asciidoctor-revealjs -v -D slides fcs-endpoint-dev-slides/index.adoc

  # Copy images/styles/...
  cp -R -v fcs-endpoint-dev-slides/images slides/
  cp -R -v fcs-endpoint-dev-slides/css slides/
  cp -R -v fcs-endpoint-dev-slides/js slides/

  # Display slides, visit "localhost:8000"
  cd slides/
  python3 -m http.server 8000
  # or simply open the "index.html"
  ```

## Historical Resources

To be found under [`historical/`](historical/):

- [`documents/`](historical/documents/) -- specification documents
- [`examples/`](historical/examples/) -- examples from specification
- [`schema/`](historical/schema/) -- `xsd` schema files
- [`software/`](historical/software/)
    - [`mpi-endpoint/`](historical/software/mpi-endpoint/) -- CQL Search servlet

## Webpage with Builds

A static overview webpage with _latest_ document builds is available at https://clarin-eric.github.io/fcs-misc/.

To update this page (only manually), go to [Actions: _publish to \<gh-pages\>_](https://github.com/clarin-eric/fcs-misc/actions/workflows/publish-gh-pages.yml) and trigger a rebuild with the _Run workflow_ button on the `main` branch.
