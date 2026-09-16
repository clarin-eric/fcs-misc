# https://docs.asciidoctor.org/pdf-converter/latest/extend/
# https://www.rubydoc.info/gems/asciidoctor-pdf/Asciidoctor/PDF/Converter
# https://www.sitepoint.com/hackable-pdf-typesetting-in-ruby-with-prawn/

# https://github.com/asciidoctor/asciidoctor-pdf/blob/v2.3.x/docs/modules/extend/examples/pdf-converter-custom-title-page.rb
# https://github.com/asciidoctor/asciidoctor-pdf/blob/v2.3.x/lib/asciidoctor/pdf/converter.rb

class PDFConverterCustomTitlePage < (Asciidoctor::Converter.for 'pdf')
  register_for 'pdf'

  def ink_title_page doc
    # QUESTION: allow alignment per element on title page?
    title_text_align = (@theme.title_page_text_align || @base_text_align).to_sym

    if @theme.title_page_logo_display != 'none' && (logo_image_path = (doc.attr 'title-logo-image') || (logo_image_from_theme = @theme.title_page_logo_image))
      if (logo_image_path.include? ':') && logo_image_path =~ ImageAttributeValueRx
        logo_image_attrs = (::Asciidoctor::AttributeList.new $2).parse %w(alt width height)
        if logo_image_from_theme
          relative_to_imagesdir = false
          logo_image_path = apply_subs_discretely doc, $1, subs: [:attributes], imagesdir: @themesdir
          logo_image_path = ThemeLoader.resolve_theme_asset logo_image_path, @themesdir unless (::File.absolute_path? logo_image_path) || (doc.is_uri? logo_image_path)
        else
          relative_to_imagesdir = true
          logo_image_path = $1
        end
      else
        logo_image_attrs = {}
        relative_to_imagesdir = false
        if logo_image_from_theme
          logo_image_path = apply_subs_discretely doc, logo_image_path, subs: [:attributes], imagesdir: @themesdir
          logo_image_path = ThemeLoader.resolve_theme_asset logo_image_path, @themesdir unless (::File.absolute_path? logo_image_path) || (doc.is_uri? logo_image_path)
        end
      end
      if (::Asciidoctor::Image.target_and_format logo_image_path)[1] == 'pdf'
        log :error, %(PDF format not supported for title page logo image: #{logo_image_path})
      else
        logo_image_attrs['target'] = logo_image_path
        # NOTE: at the very least, title_text_align will be a valid alignment value
        logo_image_attrs['align'] = [(logo_image_attrs.delete 'align'), @theme.title_page_logo_align, title_text_align.to_s].find {|val| (BlockAlignmentNames.include? val) }
        if (logo_image_top = logo_image_attrs['top'] || @theme.title_page_logo_top)
          initial_y, @y = @y, (resolve_top logo_image_top)
        end
        # NOTE: pinned option keeps image on same page
        indent (@theme.title_page_logo_margin_left || 0), (@theme.title_page_logo_margin_right || 0) do
          # FIXME: add API to Asciidoctor for creating blocks outside of extensions
          convert_image (::Asciidoctor::Block.new doc, :image, content_model: :empty, attributes: logo_image_attrs), relative_to_imagesdir: relative_to_imagesdir, pinned: true
        end
        @y = initial_y if initial_y
      end
    end

    theme_font :title_page do
      if (title_top = @theme.title_page_title_top)
        @y = resolve_top title_top
      end
      unless @theme.title_page_title_display == 'none'
        doctitle = doc.doctitle partition: true
        move_down @theme.title_page_title_margin_top || 0
        indent (@theme.title_page_title_margin_left || 0), (@theme.title_page_title_margin_right || 0) do
          theme_font :title_page_title do
            ink_prose doctitle.main, align: title_text_align, margin: 0
          end
        end
        move_down @theme.title_page_title_margin_bottom || 0
      end
      if @theme.title_page_subtitle_display != 'none' && (subtitle = (doctitle || (doc.doctitle partition: true)).subtitle)
        move_down @theme.title_page_subtitle_margin_top || 0
        indent (@theme.title_page_subtitle_margin_left || 0), (@theme.title_page_subtitle_margin_right || 0) do
          theme_font :title_page_subtitle do
            ink_prose subtitle, align: title_text_align, margin: 0
          end
        end
        move_down @theme.title_page_subtitle_margin_bottom || 0
      end
      if @theme.title_page_authors_display != 'none' && (doc.attr? 'authors')
        move_down @theme.title_page_authors_margin_top || 0
        indent (@theme.title_page_authors_margin_left || 0), (@theme.title_page_authors_margin_right || 0) do
          generic_authors_content = @theme.title_page_authors_content
          authors_content = {
            name_only: @theme.title_page_authors_content_name_only || generic_authors_content,
            with_email: @theme.title_page_authors_content_with_email || generic_authors_content,
            with_url: @theme.title_page_authors_content_with_url || generic_authors_content,
          }
          authors = doc.authors.map.with_index do |author, idx|
            with_author doc, author, idx == 0 do
              author_content_key = (url = doc.attr 'url') ? ((url.start_with? 'mailto:') ? :with_email : :with_url) : :name_only
              if (author_content = authors_content[author_content_key])
                apply_subs_discretely doc, author_content, drop_lines_with_unresolved_attributes: true, imagesdir: @themesdir
              else
                doc.attr 'author'
              end
            end
          end.join @theme.title_page_authors_delimiter
          theme_font :title_page_authors do
            ink_prose authors, align: title_text_align, margin: 0, normalize: true
          end
        end
        move_down @theme.title_page_authors_margin_bottom || 0
      end
      if @theme.title_page_revision_display != 'none'
        if (revision_content = @theme.title_page_revision_content)
          revision_content = apply_subs_discretely doc, revision_content, drop_lines_with_unresolved_attributes: true, imagesdir: @themesdir
        else
          delimiters = [', ', ': ']
          if (delimiter_overrides = @theme.title_page_revision_delimiter)
            delimiter_overrides = [delimiter_overrides] unless Array === delimiter_overrides
            delimiters[0..(delimiter_overrides.size - 1)] = delimiter_overrides
          end
          revision_content = (doc.attr? 'revnumber') ? [([(doc.attr 'version-label'), (doc.attr 'revnumber')].compact.join ' ')] : []
          if doc.attr? 'revdate'
            revision_content << delimiters[0] unless revision_content.empty?
            revision_content << (doc.attr 'revdate')
          end
          if doc.attr? 'revremark'
            revision_content << delimiters[1] unless revision_content.empty?
            revision_content << (doc.attr 'revremark')
          end
          revision_content = revision_content.join
        end
        unless revision_content.empty?
          move_down @theme.title_page_revision_margin_top || 0
          indent (@theme.title_page_revision_margin_left || 0), (@theme.title_page_revision_margin_right || 0) do
            theme_font :title_page_revision do
              ink_prose revision_content, align: title_text_align, margin: 0, normalize: false
            end
          end
          move_down @theme.title_page_revision_margin_bottom || 0
        end
      end
      if @theme.title_page_acknowledgements_display != 'none'
        if doc.attr? 'acknowledgements'
          acknowledgements_content = doc.attr 'acknowledgements'
          acknowledgements_content = apply_subs_discretely doc, acknowledgements_content, drop_lines_with_unresolved_attributes: true, imagesdir: @themesdir
        end
        if @theme.title_page_acknowledgements_content
          acknowledgements_content = apply_subs_discretely doc, @theme.title_page_acknowledgements_content, drop_lines_with_unresolved_attributes: true, imagesdir: @themesdir
        end
        unless acknowledgements_content && acknowledgements_content.empty?
          move_down @theme.title_page_acknowledgements_margin_top || 0
          indent (@theme.title_page_acknowledgements_margin_left || 0), (@theme.title_page_acknowledgements_margin_right || 0) do
            theme_font :title_page_acknowledgements do
              acknowledgements_text_align = title_text_align
              if @theme.title_page_acknowledgements_text_align
                acknowledgements_text_align = @theme.title_page_acknowledgements_text_align.to_sym
              end
              ink_prose acknowledgements_content, align: acknowledgements_text_align, margin: 0, normalize: false
            end
          end
          move_down @theme.title_page_acknowledgements_margin_bottom || 0
        end
      end
    end
  end

end
