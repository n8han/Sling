$(document).ready(function() {
  $('#body').val(doc.body);
  $('#body-preview').html(makeHtml(doc.body));
  $('#body').keypress(function() {
    $('#body-preview').html(makeHtml($(this).val()));
  });
  $('#shade').click(function() {
    if ($('#edit').is(':hidden')) {
      $('#edit, #fixed').slideDown(function() {
        $('#shade').attr('src', '/css/ship-left.gif');
      });
    }
    else {
      $('#edit, #fixed').slideUp(function() {
        $('#shade').attr('src', '/css/ship-up.gif');
      });
    }
  });
  $('#form').submit(function() {
    doc.body = $('#body').val()
    $.ajax( {
      type: 'PUT',
      url: doc_url,
      dataType: 'json', 
      data: JSON.stringify(doc),
      error: function(XMLHttpRequest, textStatus, errorThrown) {
        alert('Save failed: ' + textStatus);
      },
      complete: function(req) {
        var resp = $.httpData(req, 'json')
        doc._rev = resp.rev;
      }
    } )
    return false;
  } );
});