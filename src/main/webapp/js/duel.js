function makeHtml(md) {
  return new Showdown.converter().makeHtml(dueval(md));
}

function dueval(str) {
  var re = /#\{[^\}]*\}/g;
  
  var text = ('' + str).split(re);
  var code = str.match(re) || [];
  var out = [], i = 0, j = 0;
  while (i < text.length || j < code.length) {
    if (i < text.length) out.push(text[i]);
    if (j < code.length) try {
      out.push(eval(code[j].substr(1)));
    } catch (err) {
      out.push("[" + err + "]")
    }
    i++; j++;
  }
  return out.join("");
}

