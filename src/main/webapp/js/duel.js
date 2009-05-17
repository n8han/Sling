function makeHtml(md) {
  return new Showdown.converter().makeHtml(dusmart(dueval(md)));
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

function dusmart(s) {
  s = s.replace(/(\S)'/g, "$1&rsquo;");
  s = s.replace(/'(\S)/g, "&lsquo;$1");

  s = s.replace(/(\S)"/g, "$1&rdquo;");
  s = s.replace(/"(\S)/g, "&ldquo;$1");

  s = s.replace(/([^-])---([^-])/g, "$1&mdash;$2");
  s = s.replace(/([^-])--([^-])/g, "$1&ndash;$2");

  return s;
}
